import { useEffect, useMemo, useState } from 'react'
import './App.css'

type AuthUser = {
  email: string
  name: string
  picture?: string
}

type EmailMessage = {
  id: number
  sender: string
  subject: string
  body?: string
  category: string
  needsReply: boolean
  confidence: number
  classificationReason: string
  receivedAt: string
}

type ReplyDraft = {
  id: number
  emailId: number
  sender: string
  subject: string
  emailBody: string
  category: string
  draftContent: string
  status: string
  updatedAt: string
}

type DashboardData = {
  totalEmails: number
  needsReply: number
  skipped: number
  pendingDrafts: number
  sentDrafts: number
  emails: EmailMessage[]
  drafts: ReplyDraft[]
}

type ApiError = {
  message?: string
  error?: string
}

type DashboardTab = 'fetched' | 'needs-reply' | 'skipped' | 'drafts' | 'sent'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

function App() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [emailDetail, setEmailDetail] = useState<EmailMessage | null>(null)
  const [draftEdits, setDraftEdits] = useState<Record<number, string>>({})
  const [activeTab, setActiveTab] = useState<DashboardTab>('needs-reply')
  const [isLoading, setIsLoading] = useState(false)
  const [isProcessing, setIsProcessing] = useState(false)
  const [error, setError] = useState('')

  const isDashboard = useMemo(() => window.location.pathname === '/dashboard', [])
  const emailIdFromPath = useMemo(() => {
    const match = window.location.pathname.match(/^\/emails\/(\d+)$/)
    return match ? Number(match[1]) : null
  }, [])
  const isEmailPage = emailIdFromPath !== null

  useEffect(() => {
    if (!isDashboard && !isEmailPage) {
      return
    }

    fetchCurrentUser()
  }, [isDashboard, isEmailPage])

  async function apiFetch<T>(path: string, options: RequestInit = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers ?? {}),
      },
    })

    if (response.status === 401 || response.status === 403) {
      throw new Error('Please login with Google to continue.')
    }

    if (!response.ok) {
      let apiError: ApiError = {}
      try {
        apiError = (await response.json()) as ApiError
      } catch {
        // Some upstream errors may not return JSON.
      }

      throw new Error(apiError.message || apiError.error || `Request failed with status ${response.status}`)
    }

    return (await response.json()) as T
  }

  async function fetchCurrentUser() {
    setIsLoading(true)
    setError('')

    try {
      const currentUser = await apiFetch<AuthUser>('/api/auth/me')
      setUser(currentUser)
      if (isDashboard) {
        await refreshDashboard()
      }
      if (emailIdFromPath !== null) {
        await fetchEmailDetail(emailIdFromPath)
      }
    } catch (err) {
      setUser(null)
      setDashboard(null)
      setEmailDetail(null)
      setError(err instanceof Error ? err.message : 'Unable to load dashboard.')
    } finally {
      setIsLoading(false)
    }
  }

  async function refreshDashboard() {
    const data = await apiFetch<DashboardData>('/api/draftly/dashboard')
    setDashboard(data)

    const edits: Record<number, string> = {}
    data.drafts.forEach((draft) => {
      edits[draft.id] = draft.draftContent
    })
    setDraftEdits(edits)
  }

  async function processInbox() {
    setIsProcessing(true)
    setError('')

    try {
      const data = await apiFetch<DashboardData>('/api/draftly/inbox/process', {
        method: 'POST',
      })
      setDashboard(data)

      const edits: Record<number, string> = {}
      data.drafts.forEach((draft) => {
        edits[draft.id] = draft.draftContent
      })
      setDraftEdits(edits)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to process inbox.')
    } finally {
      setIsProcessing(false)
    }
  }

  async function fetchEmailDetail(id: number) {
    const data = await apiFetch<EmailMessage>(`/api/draftly/emails/${id}`)
    setEmailDetail(data)
  }

  async function updateDraft(id: number) {
    const content = draftEdits[id]
    await apiFetch<ReplyDraft>(`/api/draftly/drafts/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ content }),
    })
    await refreshDashboard()
  }

  async function runDraftAction(id: number, action: 'approve' | 'reject' | 'send') {
    setError('')

    try {
      await apiFetch<ReplyDraft>(`/api/draftly/drafts/${id}/${action}`, {
        method: 'POST',
      })
      await refreshDashboard()
    } catch (err) {
      setError(err instanceof Error ? err.message : `Unable to ${action} draft.`)
    }
  }

  function loginWithGoogle() {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`
  }

  const skippedEmails = dashboard?.emails.filter((email) => !email.needsReply) ?? []
  const needsReplyEmails = dashboard?.emails.filter((email) => email.needsReply) ?? []
  const actionableDrafts = dashboard?.drafts.filter((draft) => draft.status !== 'SENT' && draft.status !== 'REJECTED') ?? []
  const sentDrafts = dashboard?.drafts.filter((draft) => draft.status === 'SENT') ?? []

  if (isEmailPage) {
    return (
      <main className="app-shell dashboard-shell">
        <section className="email-page">
          <button className="secondary back-button" onClick={() => (window.location.href = '/dashboard')}>
            Back to dashboard
          </button>

          {isLoading && <div className="status-card">Loading email...</div>}

          {!isLoading && error && (
            <div className="status-card error">
              <p>{error}</p>
              {!user && <button onClick={loginWithGoogle}>Login with Google</button>}
            </div>
          )}

          {!isLoading && emailDetail && (
            <article className="email-detail-card">
              <div className="reader-meta">
                <span>{formatCategory(emailDetail.category)}</span>
                <span>{formatDate(emailDetail.receivedAt)}</span>
              </div>
              <h1>{emailDetail.subject || '(No subject)'}</h1>
              <p className="sender-line">{emailDetail.sender}</p>
              <div className="reason-box">
                <strong>{emailDetail.needsReply ? 'Reply decision' : 'Skipped reason'}</strong>
                <p>{emailDetail.classificationReason}</p>
              </div>
              <div className="reader-section-title">Email content</div>
              <div className="mail-body full">
                {emailDetail.body || 'No readable body was stored for this email yet. Go back and click Process Inbox again to refetch it.'}
              </div>
            </article>
          )}
        </section>
      </main>
    )
  }

  if (isDashboard) {
    return (
      <main className="app-shell dashboard-shell">
        <section className="dashboard">
          <header className="dashboard-header">
            <div>
              <p className="eyebrow">Draftly Dashboard</p>
              <h1>Gmail AI Reply Agent</h1>
              <p className="subtext">Process emails, skip noise, and review AI reply drafts before sending.</p>
            </div>

            {user && (
              <div className="profile-card compact">
                {user.picture && <img src={user.picture} alt={user.name} />}
                <div>
                  <span>Signed in as</span>
                  <strong>{user.name}</strong>
                  <p>{user.email}</p>
                </div>
              </div>
            )}
          </header>

          {isLoading && <div className="status-card">Loading your dashboard...</div>}

          {!isLoading && error && (
            <div className="status-card error">
              <p>{error}</p>
              {!user && <button onClick={loginWithGoogle}>Login with Google</button>}
            </div>
          )}

          {!isLoading && user && (
            <>
              <section className="command-bar">
                <div>
                  <strong>Primary inbox sync</strong>
                  <p>Fetches recent Primary emails, filters noisy messages, analyzes your sent-mail style, and prepares LLM drafts only for mails that need your reply.</p>
                </div>
                <button onClick={processInbox} disabled={isProcessing}>
                  {isProcessing ? 'Processing Inbox...' : 'Process Inbox'}
                </button>
              </section>

              {dashboard && (
                <>
                  <section className="stats-grid">
                    <Stat label="Fetched" value={dashboard.totalEmails} active={activeTab === 'fetched'} onClick={() => setActiveTab('fetched')} />
                    <Stat label="Need reply" value={dashboard.needsReply} tone="green" active={activeTab === 'needs-reply'} onClick={() => setActiveTab('needs-reply')} />
                    <Stat label="Skipped" value={dashboard.skipped} tone="amber" active={activeTab === 'skipped'} onClick={() => setActiveTab('skipped')} />
                    <Stat label="Drafts" value={dashboard.pendingDrafts} active={activeTab === 'drafts'} onClick={() => setActiveTab('drafts')} />
                    <Stat label="Sent" value={dashboard.sentDrafts} active={activeTab === 'sent'} onClick={() => setActiveTab('sent')} />
                  </section>

                  <section className="workspace-panel">
                    <div className="tabs" role="tablist" aria-label="Draftly mailbox views">
                      <Tab label="Fetched" active={activeTab === 'fetched'} onClick={() => setActiveTab('fetched')} />
                      <Tab label="Needs Reply" active={activeTab === 'needs-reply'} onClick={() => setActiveTab('needs-reply')} />
                      <Tab label="Skipped" active={activeTab === 'skipped'} onClick={() => setActiveTab('skipped')} />
                      <Tab label="Drafts" active={activeTab === 'drafts'} onClick={() => setActiveTab('drafts')} />
                      <Tab label="Sent" active={activeTab === 'sent'} onClick={() => setActiveTab('sent')} />
                    </div>

                    {activeTab === 'fetched' && (
                      <EmailSection
                        title="Fetched Primary Emails"
                        description="Every Primary inbox mail processed from the latest sync."
                        emails={dashboard.emails}
                      />
                    )}

                    {activeTab === 'needs-reply' && (
                      <EmailSection
                        title="Needs Reply"
                        description="Emails classified as requiring your response. Open a mail to inspect the decision."
                        emails={needsReplyEmails}
                      />
                    )}

                    {activeTab === 'skipped' && (
                      <EmailSection
                        title="Skipped Emails"
                        description="Notifications, OTPs, promotions, newsletters, and other mails that do not require a personal reply."
                        emails={skippedEmails}
                      />
                    )}

                    {activeTab === 'drafts' && (
                      <DraftSection
                        drafts={actionableDrafts}
                        draftEdits={draftEdits}
                        setDraftEdits={setDraftEdits}
                        updateDraft={updateDraft}
                        runDraftAction={runDraftAction}
                      />
                    )}

                    {activeTab === 'sent' && <SentSection drafts={sentDrafts} />}
                  </section>
                </>
              )}
            </>
          )}
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="login-panel">
        <p className="eyebrow">Draftly</p>
        <h1>Write Gmail replies in your own style.</h1>
        <p className="subtext">
          Connect your Google account to classify emails, skip noise, and prepare reply drafts for review.
        </p>
        <div className="actions">
          <button onClick={loginWithGoogle}>Login with Google</button>
          <button className="secondary" onClick={loginWithGoogle}>Sign up with Google</button>
        </div>
      </section>
    </main>
  )
}

function Stat({
  label,
  value,
  tone = 'default',
  active,
  onClick,
}: {
  label: string
  value: number
  tone?: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button className={`stat-card stat-${tone} ${active ? 'active' : ''}`} onClick={onClick}>
      <span>{label}</span>
      <strong>{value}</strong>
    </button>
  )
}

function Tab({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button className={`tab-button ${active ? 'active' : ''}`} role="tab" aria-selected={active} onClick={onClick}>
      {label}
    </button>
  )
}

function EmailSection({ title, description, emails }: { title: string; description: string; emails: EmailMessage[] }) {
  return (
    <section className="mail-section">
      <div className="panel-heading">
        <div>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
        <span>{emails.length}</span>
      </div>
      {emails.length === 0 && <p className="empty-text">No emails in this section yet.</p>}
      <div className="mail-table">
        {emails.map((email) => (
          <button className="mail-row" key={email.id} onClick={() => (window.location.href = `/emails/${email.id}`)}>
            <span className={`category-dot category-${email.needsReply ? 'reply' : 'skip'}`} />
            <div>
              <strong>{email.subject || '(No subject)'}</strong>
              <small>{email.sender}</small>
            </div>
            <em>{formatCategory(email.category)}</em>
            <time>{formatDate(email.receivedAt)}</time>
          </button>
        ))}
      </div>
    </section>
  )
}

function DraftSection({
  drafts,
  draftEdits,
  setDraftEdits,
  updateDraft,
  runDraftAction,
}: {
  drafts: ReplyDraft[]
  draftEdits: Record<number, string>
  setDraftEdits: React.Dispatch<React.SetStateAction<Record<number, string>>>
  updateDraft: (id: number) => Promise<void>
  runDraftAction: (id: number, action: 'approve' | 'reject' | 'send') => Promise<void>
}) {
  return (
    <section className="mail-section">
      <div className="panel-heading">
        <div>
          <h2>Drafts Pending Action</h2>
          <p>Review the generated reply, edit if needed, approve, and send.</p>
        </div>
        <span>{drafts.length}</span>
      </div>
      {drafts.length === 0 && <p className="empty-text">No drafts waiting for action.</p>}
      <div className="draft-list">
        {drafts.map((draft) => (
          <article className="draft-card" key={draft.id}>
            <div className="card-topline">
              <span>{draft.sender}</span>
              <strong className={`status-pill status-${draft.status.toLowerCase()}`}>{prettyStatus(draft.status)}</strong>
            </div>
            <h3>{draft.subject}</h3>
            <details className="mail-details">
              <summary>Original email</summary>
              <p>{draft.emailBody}</p>
            </details>
            <textarea
              value={draftEdits[draft.id] ?? draft.draftContent}
              onChange={(event) =>
                setDraftEdits((current) => ({
                  ...current,
                  [draft.id]: event.target.value,
                }))
              }
            />
            <div className="actions">
              {draft.status === 'APPROVED' ? (
                <button className="send" onClick={() => runDraftAction(draft.id, 'send')}>
                  Send Approved Reply
                </button>
              ) : (
                <>
                  <button onClick={() => runDraftAction(draft.id, 'approve')}>Approve</button>
                  <button className="secondary" onClick={() => updateDraft(draft.id)}>Save Edit</button>
                  <button className="secondary" onClick={() => runDraftAction(draft.id, 'reject')}>Reject</button>
                </>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function SentSection({ drafts }: { drafts: ReplyDraft[] }) {
  return (
    <section className="mail-section">
      <div className="panel-heading">
        <div>
          <h2>Sent From Draftly</h2>
          <p>Replies sent through this workspace.</p>
        </div>
        <span>{drafts.length}</span>
      </div>
      {drafts.length === 0 && <p className="empty-text">No replies have been sent from Draftly yet.</p>}
      <div className="sent-list">
        {drafts.map((draft) => (
          <article className="sent-card" key={draft.id}>
            <div className="card-topline">
              <span>{draft.sender}</span>
              <strong className="status-pill status-sent">sent</strong>
            </div>
            <h3>{draft.subject}</h3>
            <p>{draft.draftContent}</p>
            <time>{formatDate(draft.updatedAt)}</time>
          </article>
        ))}
      </div>
    </section>
  )
}

function prettyStatus(status: string) {
  return status.toLowerCase().replaceAll('_', ' ')
}

function formatCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ')
}

function formatDate(value: string) {
  if (!value) {
    return ''
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default App
