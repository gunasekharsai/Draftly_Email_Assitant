# Capstone Project One-Pager

## Selected Topic
**Draftly: Gmail AI Reply Agent**

## Project Goal
Build a backend system that connects securely with Gmail, reads recent or unread emails, generates AI-powered reply drafts, and allows the user to review, edit, approve, or reject every draft before anything is sent.

## Step-by-Step Approach

1. **Understand requirements and scope**
   - Study the Draftly case study and identify core flows: Gmail connection, email fetch, AI draft generation, review/approval, sending, logging, and error handling.
   - Define the MVP as a backend-first system with REST APIs and a simple review workflow.

2. **Design the system architecture**
   - Plan modules for authentication, Gmail integration, AI draft generation, draft management, sending, logging, and user preferences.
   - Design the database schema for users, OAuth tokens, fetched emails, drafts, statuses, logs, and preferences.

3. **Implement Gmail authentication**
   - Use OAuth2 to connect a user's Gmail account securely.
   - Store encrypted access/refresh tokens and support logout or token revocation.

4. **Fetch and process emails**
   - Use the Gmail API to fetch unread or recent emails with sender, subject, body, timestamp, message ID, and thread ID.
   - Clean and structure email content before passing it to the AI model.

5. **Generate AI reply drafts**
   - Create prompts using email context, thread history, tone preference, user signature, and recent sent-email style.
   - Generate draft replies in tones such as formal, concise, or friendly.

6. **Build review and approval APIs**
   - Provide endpoints to view, edit, approve, reject, and track drafts.
   - Ensure only approved drafts can move to the sending stage.

7. **Send replies safely**
   - Send approved replies through the Gmail API while preserving correct thread metadata.
   - Add retries, idempotency, and clear failure handling for expired tokens, API errors, or failed sends.

8. **Test, document, and deliver**
   - Test major flows end to end: connect Gmail, fetch email, generate draft, approve, send, and log result.
   - Prepare README documentation, API details, design decisions, GitHub repository link, and demo video.

## Expected Outcome
A reliable, secure, and extensible Gmail AI assistant backend that reduces repetitive email drafting while keeping the user fully in control.
