import { useEffect, useState } from 'react'
import {
  api,
  ApiError,
  DOCUMENT_TYPES,
  type DocumentRequest,
  type DocumentResponse,
  type DocumentType,
} from './api'

// Form inputs hold strings, so the form keeps its own string-based copy of the
// document and converts back to numbers/nulls only when saving.
interface LineForm {
  description: string
  quantity: string
  unitPrice: string
  amount: string
}

interface Form {
  documentType: DocumentType
  vendorName: string
  documentNumber: string
  documentDate: string
  currency: string
  totalAmount: string
  lineItems: LineForm[]
}

const str = (v: string | number | null) => (v == null ? '' : String(v))
const textOrNull = (s: string) => (s.trim() === '' ? null : s.trim())
const numberOrNull = (s: string) => (s.trim() === '' ? null : Number(s))

function toForm(doc: DocumentResponse): Form {
  return {
    documentType: doc.documentType,
    vendorName: str(doc.vendorName),
    documentNumber: str(doc.documentNumber),
    documentDate: str(doc.documentDate),
    currency: str(doc.currency),
    totalAmount: str(doc.totalAmount),
    lineItems: doc.lineItems.map((li) => ({
      description: li.description,
      quantity: str(li.quantity),
      unitPrice: str(li.unitPrice),
      amount: str(li.amount),
    })),
  }
}

function toRequest(form: Form): DocumentRequest {
  return {
    documentType: form.documentType,
    vendorName: textOrNull(form.vendorName),
    documentNumber: textOrNull(form.documentNumber),
    documentDate: textOrNull(form.documentDate),
    currency: textOrNull(form.currency),
    totalAmount: numberOrNull(form.totalAmount),
    lineItems: form.lineItems.map((li) => ({
      description: li.description,
      quantity: numberOrNull(li.quantity),
      unitPrice: numberOrNull(li.unitPrice),
      amount: numberOrNull(li.amount),
    })),
  }
}

interface Props {
  id: number
  onSaved: () => void
  onDeleted: () => void
}

export default function DocumentEditor({ id, onSaved, onDeleted }: Props) {
  const [doc, setDoc] = useState<DocumentResponse | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    api.get(id).then((d) => {
      setDoc(d)
      setForm(toForm(d))
    })
  }, [id])

  if (!doc || !form) return <p className="muted">Loading…</p>

  const set = (field: keyof Omit<Form, 'lineItems'>, value: string) => setForm({ ...form, [field]: value })

  const setLine = (index: number, field: keyof LineForm, value: string) =>
    setForm({
      ...form,
      lineItems: form.lineItems.map((li, i) => (i === index ? { ...li, [field]: value } : li)),
    })

  async function save() {
    setFieldErrors({})
    setMessage(null)
    try {
      const saved = await api.update(id, toRequest(form!))
      setDoc(saved)
      setForm(toForm(saved))
      setMessage('Saved')
      onSaved()
    } catch (e) {
      if (e instanceof ApiError) {
        setFieldErrors(e.errors ?? {})
        setMessage(e.message)
      }
    }
  }

  async function remove() {
    await api.remove(id)
    onDeleted()
  }

  // Shows the backend's validation message under a field, if there is one.
  const err = (field: string) => fieldErrors[field] && <small className="error">{fieldErrors[field]}</small>

  return (
    <div>
      <div className="detail-header">
        <h2>{doc.fileName}</h2>
        <span className={`badge ${doc.status.toLowerCase()}`}>{doc.status}</span>
      </div>

      <div className="fields">
        <label>
          Type
          <select value={form.documentType} onChange={(e) => set('documentType', e.target.value)}>
            {DOCUMENT_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <label>
          Vendor
          <input value={form.vendorName} onChange={(e) => set('vendorName', e.target.value)} />
          {err('vendorName')}
        </label>
        <label>
          Number
          <input value={form.documentNumber} onChange={(e) => set('documentNumber', e.target.value)} />
          {err('documentNumber')}
        </label>
        <label>
          Date
          <input type="date" value={form.documentDate} onChange={(e) => set('documentDate', e.target.value)} />
        </label>
        <label>
          Currency
          <input value={form.currency} onChange={(e) => set('currency', e.target.value)} maxLength={3} />
          {err('currency')}
        </label>
        <label>
          Total
          <input type="number" step="0.01" value={form.totalAmount} onChange={(e) => set('totalAmount', e.target.value)} />
          {err('totalAmount')}
        </label>
      </div>

      <table>
        <thead>
          <tr>
            <th>Description</th>
            <th>Qty</th>
            <th>Unit price</th>
            <th>Amount</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {form.lineItems.map((li, i) => (
            <tr key={i}>
              <td>
                <input value={li.description} onChange={(e) => setLine(i, 'description', e.target.value)} />
                {err(`lineItems[${i}].description`)}
              </td>
              <td>
                <input type="number" value={li.quantity} onChange={(e) => setLine(i, 'quantity', e.target.value)} />
              </td>
              <td>
                <input type="number" step="0.01" value={li.unitPrice} onChange={(e) => setLine(i, 'unitPrice', e.target.value)} />
              </td>
              <td>
                <input type="number" step="0.01" value={li.amount} onChange={(e) => setLine(i, 'amount', e.target.value)} />
              </td>
              <td>
                <button
                  className="icon"
                  title="Remove line"
                  onClick={() => setForm({ ...form, lineItems: form.lineItems.filter((_, j) => j !== i) })}
                >
                  ×
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button
        className="secondary"
        onClick={() =>
          setForm({
            ...form,
            lineItems: [...form.lineItems, { description: '', quantity: '', unitPrice: '', amount: '' }],
          })
        }
      >
        + Add line
      </button>

      <div className="actions">
        <button onClick={save}>Save as reviewed</button>
        <button className="danger" onClick={remove}>
          Delete
        </button>
        {message && <span className={message === 'Saved' ? 'ok' : 'error'}>{message}</span>}
      </div>
    </div>
  )
}
