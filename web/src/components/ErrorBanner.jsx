export default function ErrorBanner({ message }) {
  if (!message) return null
  return <p className="state state--error">{message}</p>
}
