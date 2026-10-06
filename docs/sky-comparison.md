# The gameplay sky, three ways

`sky-comparison.png` is the same level, the same frame, rendered three times. It exists so the question in
[HOLDFAST.md](../HOLDFAST.md) can be answered by looking rather than argued.

| | what it is | what it costs |
|---|---|---|
| **1. now** | flat `#87CEEB` | the ground floats in sky-blue |
| **2. dusk** | the sky the title screen already paints | the bats vanish into the dark upper band |
| **3. dusk + a lighter bat** | the same, with one sprite changed | nothing visible |

**Rendering it turned up two things the question itself did not mention.**

- **Dusk fixes something nobody had raised.** With the flat sky the area *below the ground* is the same blue as
  the sky, so the ground reads as a strip floating in air. With the gradient it is black, which reads as earth.
  That is a real improvement and it is not what the question was about.
- **Dusk costs something.** The bats are dark slate (`#2F4F4F`), chosen to read against a light sky. Against the
  dark upper band they nearly disappear — compare the two on the left of panel 2 with the same two in panel 1.

**So it is not a two-way choice, and panel 3 is why.** The bats are the only thing that suffers, and they are one
sprite. Panel 3 is panel 2 with the bat's own grid recoloured from `M` (dark slate) to `L` (stone light) — the
spider and the bomb keep `M`, so nothing else in the game changes. Both problems go.

**Panel 3 is a demonstration, not a proposal.** It shows that the choice is not "dusk or readable bats"; it is a
sprite colour. The decision is Kinger's and nothing has been changed: the sky swap and the bat recolour were both
reverted immediately after rendering, and the tree is clean. The only thing committed is this picture and this note.
