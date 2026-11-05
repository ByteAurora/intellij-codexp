# Feature Guide And Update Notices

The dashboard information button and **Tools > CodeXP Guide and Updates** open a separate, resizable, modeless dialog centered on the IDE by default. Reopening brings the existing project dialog forward without resetting its page. Its size and position are remembered. Closing with Escape or the window close button keeps the current page for later; finishing, skipping, or explicitly dismissing the notification marks that edition as read. Destination buttons close the guide and open the actual dashboard, collection, or settings. The dialog is disposed when its project closes.

A non-modal notification offers the introduction for new profiles and unread update pages for existing profiles. The dashboard is not replaced or resized when the guide opens.

The guide has its own compact segmented tabs, a teal primary action, quiet secondary actions, and fixed page progress/navigation. These styles are scoped to the guide, not global IntelliJ controls. Buttons retain native models, focus rings, keyboard activation and accessibility. Left/right arrows switch the focused guide tab. Hover/press transitions last 160 ms, stop when hidden, and respect both CodeXP and system reduced-motion preferences; no animation timer runs at rest.

## Adding An Update

1. Append a `GuideRelease` to `CodeXPGuideCatalog.releases` in chronological order. Keep shipped IDs stable. A patch without a new guide edition does not repeat the notice.
2. Add short page titles and bodies to both message bundles. Use a new step ID if its meaning changes, so skipped versions do not collapse different changes into one page.
3. Choose a destination from dashboard, collection, or settings. Previews must read real state and must never grant or spend rewards.
4. Run `CodeXPGuideTest`, including fresh/existing profile, resume, skipped-version, localization, and narrow/HiDPI layout checks.

Read history lives in `CodeXPGuide.xml`, separate from progress and the collection ledger. Unknown future edition IDs are preserved when loading an older plugin. Resetting all data except the nickname also resets guide history. A notification is offered at most once per app session, across project windows; an unfinished guide can be offered again after restart.
