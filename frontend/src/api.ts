// TypeScript versions of the Java DTO records. These are the API contract:
// if a record changes in the backend, change it here too.

export type DocumentType = 'INVOICE' | 'RECEIPT' | 'PURCHASE_ORDER' | 'OTHER'
export type DocumentStatus = 'EXTRACTED' | 'REVIEWED'

export const DOCUMENT_TYPES: DocumentType[] = ['INVOICE', 'RECEIPT', 'PURCHASE_ORDER', 'OTHER']

export interface LineItemResponse {
  id: number
  position: number
  description: string
  quantity: number | null
  unitPrice: number | null
  amount: number | null
}

export interface DocumentResponse {
  id: number
  fileName: string
  documentType: DocumentType
  status: DocumentStatus
  vendorName: string | null
  documentNumber: string | null
  documentDate: string | null // ISO date, "2026-10-01"
  currency: string | null
  totalAmount: number | null
  lineItems: LineItemResponse[]
  createdAt: string
  updatedAt: string
}

export interface DocumentSummary {
  id: number
  fileName: string
  documentType: DocumentType
  status: DocumentStatus
  vendorName: string | null
  totalAmount: number | null
  currency: string | null
  createdAt: string
}

export interface LineItemRequest {
  description: string
  quantity: number | null
  unitPrice: number | null
  amount: number | null
}

export interface DocumentRequest {
  documentType: DocumentType
  vendorName: string | null
  documentNumber: string | null
  documentDate: string | null
  currency: string | null
  totalAmount: number | null
  lineItems: LineItemRequest[]
}

/** The backend's ProblemDetail error body. `errors` is only present for validation failures. */
export class ApiError extends Error {
  status: number
  errors: Record<string, string>

  constructor(status: number, detail: string, errors: Record<string, string> = {}) {
    super(detail)
    this.status = status
    this.errors = errors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}))
    throw new ApiError(res.status, problem.detail ?? res.statusText, problem.errors)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export const api = {
  list: () => request<DocumentSummary[]>('/api/documents'),

  get: (id: number) => request<DocumentResponse>(`/api/documents/${id}`),

  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file) // the field name must match @RequestParam("file")
    return request<DocumentResponse>('/api/documents/upload', { method: 'POST', body: form })
  },

  update: (id: number, body: DocumentRequest) =>
    request<DocumentResponse>(`/api/documents/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),

  remove: (id: number) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),
}
