import { useEffect, useState } from 'react'
import { api, ApiError, type DocumentSummary } from './api'
import DocumentEditor from './DocumentEditor'

export default function App() {
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    try {
      setDocuments(await api.list())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not reach the backend. Is it running on :8080?')
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // allow choosing the same file again
    if (!file) return
    setUploading(true)
    setError(null)
    try {
      const created = await api.upload(file)
      await refresh()
      setSelectedId(created.id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setUploading(false)
    }
  }

  return (
    <main>
      <header>
        <h1>Document Processor</h1>
        <label className={`upload ${uploading ? 'busy' : ''}`}>
          {uploading ? 'Extracting with Claude…' : 'Upload document'}
          <input
            type="file"
            accept="application/pdf,image/png,image/jpeg,image/webp"
            onChange={handleUpload}
            disabled={uploading}
          />
        </label>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="layout">
        <section className="list">
          {documents.length === 0 && <p className="muted">No documents yet. Upload a PDF or image.</p>}
          {documents.map((doc) => (
            <button
              key={doc.id}
              className={`row ${doc.id === selectedId ? 'selected' : ''}`}
              onClick={() => setSelectedId(doc.id)}
            >
              <span className="row-main">
                <strong>{doc.vendorName ?? 'Unknown vendor'}</strong>
                <span className="muted">{doc.fileName}</span>
              </span>
              <span className="row-side">
                <span>{doc.totalAmount != null ? `${doc.totalAmount} ${doc.currency ?? ''}` : '-'}</span>
                <span className={`badge ${doc.status.toLowerCase()}`}>{doc.status}</span>
              </span>
            </button>
          ))}
        </section>

        <section className="detail">
          {selectedId == null ? (
            <p className="muted">Select a document to review it.</p>
          ) : (
            <DocumentEditor
              key={selectedId} // a new key resets the editor's state when switching documents
              id={selectedId}
              onSaved={refresh}
              onDeleted={() => {
                setSelectedId(null)
                refresh()
              }}
            />
          )}
        </section>
      </div>
    </main>
  )
}
