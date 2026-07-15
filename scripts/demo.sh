#!/usr/bin/env bash
#
# ChargeSquare — uçtan uca smoke test.
#
# Tüm akışı sürer ve beklenen değerleri DOĞRULAR: giriş, connector durumu,
# başlat (201), faturalama (108.25), cüzdan (391.75), connector'ın serbest kalması,
# ikinci durdurmanın reddi (409) ve rol kontrolü (403).
#
# Temiz bir stack bekler:
#   docker compose down -v && docker compose up -d --build && ./scripts/demo.sh
#
# Ortam değişkenleriyle adresler değiştirilebilir: STATION_URL, SESSION_URL.

set -uo pipefail

STATION="${STATION_URL:-http://localhost:8081}"
SESSION="${SESSION_URL:-http://localhost:8082}"

USER_ID=7
CONNECTOR_ID=10
ENERGY=12.5
EXPECTED_COST="108.25"          # 12.5 × 8.50 + 2.00
EXPECTED_BALANCE="391.75"       # 500.00 - 108.25 (temiz stack'te)

pass=0
fail=0

ok()  { echo "  ✓ $1"; pass=$((pass + 1)); }
bad() { echo "  ✗ $1"; fail=$((fail + 1)); }

check() { # check <açıklama> <gelen> <beklenen>
  if [ "$2" = "$3" ]; then ok "$1 → $2"; else bad "$1 — beklenen: $3, gelen: $2"; fi
}

# JSON'dan tek bir alanı okur (string veya sayı). Bağımlılık eklememek için jq kullanmıyoruz.
jval() {
  echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"\|\"$2\"[[:space:]]*:[[:space:]]*[0-9.]*" \
    | head -1 | sed -E "s/\"$2\"[[:space:]]*:[[:space:]]*//; s/^\"//; s/\"$//"
}

# İstek atar; RESP_CODE ve RESP_BODY değişkenlerini doldurur.
req() { # req <method> <url> <token|""> [body]
  local method=$1 url=$2 token=$3 body=${4:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$url" --max-time 15)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out
  out=$(curl "${args[@]}")
  RESP_CODE=$(echo "$out" | tail -1)
  RESP_BODY=$(echo "$out" | sed '$d')
}

echo
echo "ChargeSquare smoke test — $STATION / $SESSION"
echo

# Compose yeni kalktıysa servislerin hazır olması birkaç saniye sürer; kısa süre bekleriz.
wait_for_health() { # wait_for_health <base-url> <ad>
  local i
  for i in $(seq 1 40); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$1/health" 2>/dev/null)" = "200" ]; then
      ok "$2 /health → 200"
      return 0
    fi
    sleep 2
  done
  bad "$2 /health → 80 sn içinde yanıt vermedi"
  return 1
}

echo "1) Servisler ayakta mı (hazır olmalarını bekliyorum)"
wait_for_health "$STATION" "station"
wait_for_health "$SESSION" "session"
if [ "$fail" -gt 0 ]; then
  echo; echo "Servisler hazır değil. Önce: docker compose up -d --build"; exit 1
fi

echo
echo "2) Giriş (admin + viewer)"
req POST "$SESSION/auth/login" "" '{"username":"admin","password":"admin123"}'
check "admin login" "$RESP_CODE" "200"
ADMIN=$(jval "$RESP_BODY" token)
check "admin rolü" "$(jval "$RESP_BODY" role)" "ADMIN"

req POST "$SESSION/auth/login" "" '{"username":"viewer","password":"viewer123"}'
VIEWER=$(jval "$RESP_BODY" token)
check "viewer login" "$RESP_CODE" "200"

echo
echo "3) Başlangıç durumu"
req GET "$STATION/connectors/$CONNECTOR_ID" "$ADMIN"
check "connector $CONNECTOR_ID durumu" "$(jval "$RESP_BODY" status)" "AVAILABLE"
if [ "$(jval "$RESP_BODY" status)" != "AVAILABLE" ]; then
  echo "     (ipucu: temiz durum için 'docker compose down -v && docker compose up -d --build')"
fi

# Cüzdanın mutlak bakiyesi ancak hiç oturum koşulmamışsa öngörülebilir (seed: 500.00).
# Daha önce oturum varsa maliyeti yine sıkı doğrularız, mutlak bakiyeyi bilgi olarak geçeriz.
req GET "$SESSION/users/$USER_ID/sessions" "$ADMIN"
PRIOR_SESSIONS=$(echo "$RESP_BODY" | grep -o '"sessionId"' | wc -l | tr -d ' ')
if [ "$PRIOR_SESSIONS" = "0" ]; then
  ok "temiz stack (önceki oturum yok) → bakiye de doğrulanacak"
  FRESH=1
else
  FRESH=0
  echo "  • $PRIOR_SESSIONS önceki oturum var → mutlak bakiye kontrolü atlanır (maliyet yine doğrulanır)"
  echo "    (sıfırlamak için: docker compose down -v && docker compose up -d --build)"
fi

echo
echo "4) Oturum başlat"
req POST "$SESSION/sessions" "$ADMIN" "{\"userId\":$USER_ID,\"connectorId\":$CONNECTOR_ID}"
check "start HTTP" "$RESP_CODE" "201"
SESSION_ID=$(jval "$RESP_BODY" sessionId)
check "oturum durumu" "$(jval "$RESP_BODY" status)" "ACTIVE"
check "tarife snapshot'landı" "$(jval "$RESP_BODY" pricePerKwh)" "8.50"
echo "     oturum id: ${SESSION_ID:-yok}"

req GET "$STATION/connectors/$CONNECTOR_ID" "$ADMIN"
check "connector artık dolu" "$(jval "$RESP_BODY" status)" "OCCUPIED"

echo
echo "5) Oturumu durdur, faturala ve tahsil et"
req POST "$SESSION/sessions/$SESSION_ID/stop" "$ADMIN" "{\"energyKwh\":$ENERGY}"
check "stop HTTP" "$RESP_CODE" "200"
check "oturum durumu" "$(jval "$RESP_BODY" status)" "COMPLETED"
check "maliyet (${ENERGY} × 8.50 + 2.00)" "$(jval "$RESP_BODY" cost)" "$EXPECTED_COST"
if [ "$FRESH" = "1" ]; then
  check "cüzdan bakiyesi (500.00 - $EXPECTED_COST)" "$(jval "$RESP_BODY" walletBalanceAfter)" "$EXPECTED_BALANCE"
else
  echo "  • cüzdan bakiyesi: $(jval "$RESP_BODY" walletBalanceAfter) (önceki oturumlar nedeniyle mutlak değer doğrulanmadı)"
fi

req GET "$STATION/connectors/$CONNECTOR_ID" "$ADMIN"
check "connector serbest kaldı" "$(jval "$RESP_BODY" status)" "AVAILABLE"

echo
echo "6) Guard'lar"
req POST "$SESSION/sessions/$SESSION_ID/stop" "$ADMIN" "{\"energyKwh\":5}"
check "ikinci durdurma reddedildi" "$RESP_CODE" "409"
check "hata kodu" "$(jval "$RESP_BODY" error)" "SESSION_NOT_ACTIVE"

req POST "$SESSION/sessions" "$ADMIN" "{\"userId\":$USER_ID,\"connectorId\":999}"
check "bilinmeyen connector" "$RESP_CODE" "404"

req POST "$SESSION/sessions" "$ADMIN" "{\"connectorId\":$CONNECTOR_ID}"
check "eksik userId" "$RESP_CODE" "400"

echo
echo "7) Yetki"
req POST "$SESSION/sessions" "" "{\"userId\":$USER_ID,\"connectorId\":$CONNECTOR_ID}"
check "token'sız başlatma" "$RESP_CODE" "401"

req POST "$SESSION/sessions" "$VIEWER" "{\"userId\":$USER_ID,\"connectorId\":$CONNECTOR_ID}"
check "viewer ile başlatma" "$RESP_CODE" "403"

req GET "$SESSION/sessions/$SESSION_ID" "$VIEWER"
check "viewer ile okuma" "$RESP_CODE" "200"

echo
echo "─────────────────────────────"
echo "  başarılı: $pass   başarısız: $fail"
echo "─────────────────────────────"
echo
[ "$fail" -eq 0 ] || exit 1
