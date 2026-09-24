# Specification Quality Checklist: Fréquences d'affichage au-delà de 120 Hz

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Validation passée à la deuxième itération (2026-09-24). Corrections faites à la première
  relecture :
  - retrait d'une affirmation non vérifiée sur les outils concurrents ;
  - retrait d'un cas limite redondant avec la User Story 2, scénario 5.
- Choix faits par défaut, sans marqueur de clarification (voir Assumptions) :
  - fréquences retenues : 144, 160, 180 et 200 Hz, parmi les valeurs mesurées ;
  - limites de résolution calculées à charge constante depuis ×1,0 à 160 Hz ;
  - quand une fréquence élevée est choisie, la résolution est abaissée d'office, avec un message,
    plutôt que de griser la fréquence ;
  - « Par défaut du jeu » reste permis à fréquence élevée.
- Décisions de l'utilisateur :
  - toutes les fréquences au-delà de 120 Hz sont « expérimental » ;
  - certaines résolutions sont impossibles à certaines fréquences ;
  - l'appli s'arrête à 200 Hz (amendement du 2026-09-24) : 207 Hz est retiré.
- La table de FR-005 repose sur un seul jeu. Un essai avec ×0,8 et le GPU au niveau 5, à 180 et
  200 Hz, permettrait de la revoir avant `/speckit-plan`, ou pendant la validation sur casque.
