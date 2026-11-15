## effect.small_change
Only a small local change followed (focus, highlight, or one or two elements). Check that it did what you meant before relying on it.

## effect.loading
The interface was still changing or loading when the check ended, so the result may not be complete yet. Look again before relying on it; do not repeat the action.

## effect.no_effect
Nothing in the interface changed after this action, so it probably did not register. Do not repeat it unchanged: try another way, such as a different element, bringing the target into view first, or another route to the same place.

## effect.no_visible_change
Nothing visible changed after this action. On a screen the element list cannot read, a tap that only focuses a box or changes hidden state shows no change, so this does not mean it failed. To type into a box there, use phone_type_text with that point (it checks the result itself); otherwise look once before repeating.

## effect.no_effect.back
Nothing in the interface changed after back; this page may ignore the system back. Do not repeat back: tap the page's own back or close control, or open the app or page again.

## effect.no_effect.scroll
Nothing moved, so you are probably already at the end in this direction (or this spot does not scroll). Before concluding there is no more content, check the direction: down shows what is further down, up what is further up.

## effect.place_changed
This action took you somewhere else: now in {{to}}. If that is not part of the task, go back.

## effect.readback_differs
The field now shows {{readback}}, which is not the intended text.

## effect.limit_reached
After this action the site or app says a limit was reached: "{{text}}". This one may not have gone through: check before counting it as done. Do not repeat it or retry today. If you are working through a list, stop here and report which items were done, which were not, and why.

## effect.stale
The interface has changed since you last read it: earlier element ids and coordinates may no longer match. Read it again before acting on it.

## guard.ways_out
Ways out: try a clearly different path (another control, search, menu or entry point); ask the user if something only they can decide or provide is missing; or deliver what you have so far and say plainly what is still missing.

## guard.repeating_action
You have done the same action on the same target {{count}} times.

## guard.no_progress
The last {{count}} actions changed nothing in the interface.

## guard.same_state
You have come back to the same state of the interface {{count}} times without reaching anything new. If you are deliberately going through items one by one, carry on.

## guard.state_cycle
You are going round the same {{states}} states of the interface ({{count}} rounds).

## guard.no_new_state
{{count}} actions in a row have not reached any state of the interface you had not already seen.

## guard.read_loop
The page has not changed, and reading it again ({{count}} times) shows nothing new.

## guard.too_many_failures
{{count}} actions in a row have failed.
## tree_poor
The accessibility tree is missing much of this screen (treeQuality), and observing again will not add the missing elements. visibleText is the text read from the screenshot; its center is a tap point on phone_tap's 0–999 x/y grid. Use element refs where they exist; tap text that has none with phone_tap x/y at its center. OCR can misread small or decorated text: do not report a garbled line as fact.

## tree_poor.no_text
The accessibility tree is missing this screen and no text could be read from its picture either (ocr: {{ocr}}). If it was still loading, wait a moment and observe once more; otherwise work from the screenshot if one is attached, or call hand_to_user.
