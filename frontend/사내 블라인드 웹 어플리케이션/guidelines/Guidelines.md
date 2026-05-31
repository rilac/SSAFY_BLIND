# Design Guidelines

## Aesthetic Stance: Brutalist with Sky Blue Theme

Raw, technical, and deliberately structured aesthetic with a calming sky blue color palette. The design conveys honesty, anonymity, and directness — perfect for an anonymous workplace discussion platform.

## Typography

**Display (Headings)**: JetBrains Mono — monospace as display, technical and raw
**Body (Text)**: Work Sans — geometric sans, clean and readable
**Mono (Data/Labels)**: JetBrains Mono — structural mono for timestamps, counts, labels

## Color Palette

**Light Mode**:
- Canvas: Light sky blue (#f0f8ff)
- Foreground: Deep navy (#0a1929)
- Accent: Bright sky blue (#0ea5e9) — primary interactive color
- Cards: Pure white with sky blue borders

**Dark Mode**:
- Canvas: Deep navy (#0a1929)
- Foreground: Light gray (#f1f5f9)
- Accent: Light cyan (#38bdf8) — primary interactive color
- Cards: Dark blue-gray (#132337) with cyan borders

## Visual Language

- Minimal border radius (2px maximum) for sharp, technical feel
- Sky blue accent for interactive elements and highlights
- Thin hairline borders to organize content
- Generous whitespace despite dense information
- Mono type for all structural elements (timestamps, counts, categories)
- Sans body type for readability in long-form content
- Dark mode toggle for user preference

## Component Patterns

- Cards with thin borders, no shadows
- Buttons with stark borders and solid fills
- Form inputs with visible borders and clear states
- Lists with hairline dividers
- Monospace labels for metadata (author, time, category)
- High-contrast focus states with sky blue accent

## Layout Principles

- Single-column feed on mobile, sidebar + feed on desktop
- Asymmetric grid when appropriate
- Dense information hierarchy without clutter
- Clear section separation with borders and spacing

## Nickname System

- Format: [Adjective] + [SSAFY Character]
- Characters: 스타티, 핏, 와이즈, 알지
- User selects from predefined adjectives during onboarding
