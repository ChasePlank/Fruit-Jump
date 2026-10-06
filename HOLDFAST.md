# Holdfast — the story

*Holdfast* is a side-scrolling climber. Its own subtitle is the premise: **a climber, a sunset, and a way home.**
This is the story behind that sentence. It is written to fit the game that already exists — five screens, one
sunset, no cutscenes — rather than to ask for systems that are not there.

## The sentence

The valley flooded. Not all at once and not dramatically: the water came up over years, the way it does in the
story the engine already tells in *The Water Line* — what is above the line goes up, what is below it stays, and
the water is not sentimental.

Everyone left. **You did not, and then you did**, and by then the road out was under sixty feet of water and the
only direction that was still dry was up.

So you climb. **The way home is above you**, which is the wrong direction for home to be and the only one left.

## Why the sunset matters

**The sunset is a clock, not a decoration.** You climb while there is light. The horizon in this game never moves
past dusk — that is not an art choice, it is the rule: **the sun stops at the same place every time, and the game
ends when it finishes setting.**

That is what the five screens are already telling:

| screen | what it is, in story terms |
|---|---|
| Title | dusk, the climber on the rock, the way home not yet started |
| Main menu | the same afternoon, one breath before |
| Customise | the last time you are standing still |
| Pause | the same place, held — nothing moves while you decide |
| **Game over** | **after the sun. The rock is empty.** |

The game-over screen already shows the same sky with the climber gone. **Nobody had to be told what that meant;
the story just has to agree with it.**

## What the climber is carrying

Not equipment. **The thing worth carrying out is the reason to come back down**, and the reason has to be small
enough to fit in a pocket: a key to a door that is underwater, a photograph, a name.

**The best version of this is the one with the least in it.** The climber does not speak and nothing is explained,
because the game has no dialogue and should not get one.

## The rule the story has to obey

**Nothing in this story may require a system the game does not have.** No NPCs, no items to collect, no dialogue,
no cutscenes, no ending that plays differently because of a choice made in scene three. The story is a *reason* for
the mechanics — jump, hookshot, keep moving up, do not fall — and the mechanics are already built.

If a later version wants to say more, it says it with **the sky**, because the sky is the one thing every screen
already shares. `Skyline.paint(..., Mood.DUSK, ...)` and `Mood.NIGHT` are the two words this story has.

## What it is about, in one line

**Going up is not the same as going home, and the sunset does not care which one you meant.**

## The one thing this story asks the game for

**Verified rather than remembered, and it is the only change the premise needs:**

| screen | sky | climber |
|---|---|---|
| TitleScreen | `Mood.DUSK` | drawn |
| MainMenu | `Mood.DUSK` | drawn |
| CustomizeScreen | `Mood.DUSK` | drawn |
| **GameplayScreen** | **`#87CEEB` — mid-day sky blue** | drawn |
| GameOverScreen | `Mood.NIGHT` | **gone** |
| PauseScreen | *no sky of its own* — an **overlay**, gameplay frozen underneath |

**So the sky is dusk on four screens and mid-day on the one the player actually looks at.** The story above says
the horizon never moves past dusk. **Right now the horizon moves at the exact moment you start playing, and moves
back when you die.**

**This is the sunset-port question, and it is Kinger's call, not mine.** Two readings and both are defensible:

- **The gameplay sky is the story's, and it should be dusk.** Then the whole game is one continuous evening and the
  title screen is telling the truth. This is what `aside` already does — its own `Skyline` paints sunset in play.
- **The gameplay sky is a readability decision, and it stays blue.** Dusk is the frame and mid-day is the room.
  Nothing is broken; the subtitle is just a sentence about the *setting*, not about the *hour*.

**I have not changed it. It was already an open question before this document existed** — what this adds is the
evidence that the question is real, and the specific line of code it lives on.

## The constraint, checked

**Every system this document names is checked against what the game has:**

| the story mentions | the engine has |
|---|---|
| climb | yes |
| jump | yes |
| hookshot | yes |
| keys, doors | yes (`keys`, `door` in the engine) |
| *"no dialogue"* | the game has none, and this document asks for none |

The engine also has **coins**, which this story does not mention and does not need. **Nothing here asks for an NPC,
a cutscene, an inventory, or a choice that changes an ending** — so nothing here can fail to be implemented.

## Open, deliberately

The premise above is mine and it is **not final** — the game is Kinger's to direct and the story is the part of it
that was left open. What is *settled* is the constraint: whatever the story becomes, it fits the game that exists
and it lives in the sky.
