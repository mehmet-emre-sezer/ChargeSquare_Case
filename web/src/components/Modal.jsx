// Basit, yeniden kullanılabilir modal. Arkaplana tıklayınca kapanır.
export default function Modal({ title, onClose, children }) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()}>
        <div className="modal__header">
          <h3 className="modal__title">{title}</h3>
          <button className="btn btn--ghost btn--sm" onClick={onClose}>Kapat</button>
        </div>
        <div className="modal__body">{children}</div>
      </div>
    </div>
  )
}
