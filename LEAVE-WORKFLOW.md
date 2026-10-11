# Admin and employee leave workflow

1. Admin configures holiday calendar coverage and all public/Poya/company holidays. Office/Hotel staff require their selected-year annual allowance; blank unknown quota remains #. Sales earn one leave after ten distinct completed working days.
2. Employee opens My Leave, selects an eligible date, enters a reason and applies. Confirmed submission shows Waiting for Chairman Sir's approval. One leave is reserved while pending.
3. The backend generates a new-request notification for active admins. Admin opens Leave Sheet, uses Pending approvals or date/department/status filters, and taps the request row.
4. Admin asks Chairman Sir using the company's normal process. The app itself does not send a question to him. Admin enters the decision note and chooses Approve or Reject.
5. Backend checks active admin access, pending status, booking reservations and leave eligibility. Only a confirmed server decision is shown as saved.
6. Employee sees Your leave has been approved/rejected by Chairman Sir in My Leave, with leave date and the admin's decision note. An employee inbox notification is generated and phone push is attempted.
7. Approval converts pending to used (no second deduction). Rejection returns the reserved day to available balance. Switching language changes the interface, not the leave/accounting data.

If no rows appear, use All Employees + All statuses and a range covering the leave date, then Refresh. Leave Sheet From/To filters use the requested leave date, not the date the request was submitted. Today/future filter dates are valid.

Push delivery needs deployed notification functions/rules, successful device registration and phone notification permission. SMS OTP and backend deployment setup are separate. The source tests do not prove these live services are configured.
