// UC-EVA-submit-evaluation with a deactivated teammate (UC-STU-deactivate-student, BR-evaluation-active-evaluatee,
// BR-submission-owed). The evaluation and roster APIs are stubbed; the backend's own tests cover the rule itself.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`. Logging in needs the real backend.
// Seed data: John (id 4) is on Team1 with Eric (5) and Jerry (6), and the course section's rubric has criteria 1 to 6.

const ok = (data) => ({ flag: true, code: 200, message: 'ok', data })

const teammate = (id, firstName, enabled = true) => ({
  id,
  username: `${firstName}@abc.edu`,
  enabled,
  firstName,
  lastName: 'Teammate',
  email: `${firstName}@abc.edu`,
  teamId: 1,
  sectionId: 2
})

const JOHN = teammate(4, 'John')
const ERIC = teammate(5, 'Eric')
const JERRY = teammate(6, 'Jerry')

const saved = (student) => ({
  evaluationId: 100 + student.id,
  week: '2023-W32',
  evaluatorId: 4,
  evaluateeId: student.id,
  evaluateeName: `${student.firstName} ${student.lastName}`,
  ratings: [1, 2, 3, 4, 5, 6].map((criterionId) => ({
    ratingId: criterionId,
    criterionId,
    criterion: `Criterion ${criterionId}`,
    actualScore: 10
  })),
  totalScore: 60,
  publicComment: '',
  privateComment: ''
})

const ROWS = '.el-table__body-wrapper .el-table__row'

describe('Submit peer evaluations with a deactivated teammate', () => {
  let teammates
  let savedEvaluations

  beforeEach(() => {
    teammates = [JOHN, ERIC, JERRY]
    savedEvaluations = []
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', /\/api\/v1\/students\/teams\/1$/, (req) => req.reply(ok(teammates))).as('team')
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', (req) => req.reply(ok(savedEvaluations))).as('saved')
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', { body: ok(null) }).as('receipt')
  })

  function stubSaves(refusedEvaluateeId, delayMs = 0) {
    cy.intercept('POST', /\/api\/v1\/evaluations$/, (req) => {
      if (req.body.evaluateeId === refusedEvaluateeId) {
        return req.reply({
          statusCode: 400,
          body: {
            flag: false,
            code: 400,
            message: 'You cannot submit a new peer evaluation of a teammate whose account is deactivated.'
          }
        })
      }
      // Held back, and only recorded as saved once released: a reload that did not wait for it would not list it
      return new Promise((resolve) => {
        setTimeout(() => {
          const row = saved(teammates.find((student) => student.id === req.body.evaluateeId))
          savedEvaluations.push(row)
          req.reply(ok(row))
          resolve()
        }, delayMs)
      })
    }).as('submit')
    cy.intercept('PUT', '**/evaluations/*', (req) => {
      req.reply(ok(req.body))
    }).as('update')
  }

  function open() {
    cy.visit('/evaluations/submit-evaluations')
    cy.wait('@team')
    cy.wait('@saved')
  }

  function rowsShouldBe(names) {
    cy.get(ROWS).should('have.length', names.length)
    names.forEach((name) => cy.get(ROWS).should('contain', name))
  }

  it('has no row for a deactivated teammate on a new submission', () => {
    teammates = [JOHN, teammate(5, 'Eric', false), JERRY]
    stubSaves()

    open()

    rowsShouldBe(['John', 'Jerry'])
    cy.get('.el-table').should('not.contain', 'Eric')
    cy.contains('button', 'Submit').should('be.visible')
  })

  it('saves the others, shows what was saved, and clears loading when one is refused (extension 9a)', () => {
    stubSaves(ERIC.id, 800)
    open()
    rowsShouldBe(['John', 'Eric', 'Jerry'])
    teammates = [JOHN, teammate(5, 'Eric', false), JERRY] // Deactivated while the form is open

    cy.contains('button', 'Submit').click()

    cy.get('.el-message--error').should('contain', 'whose account is deactivated')
    rowsShouldBe(['John', 'Jerry'])
    cy.get('.el-table').should('not.contain', 'Eric')
    cy.contains('button', 'Update').should('be.visible').and('not.have.class', 'is-loading')
    cy.wait('@receipt') // The two that were saved earn the confirmation email
  })

  it('offers a blank row for an active teammate with none saved, then submits it and updates the rest', () => {
    savedEvaluations = [saved(JOHN), saved(ERIC)]
    stubSaves()
    open()
    rowsShouldBe(['John', 'Eric', 'Jerry'])

    cy.contains('button', 'Update').click()

    cy.wait('@submit').its('request.body.evaluateeId').should('eq', JERRY.id)
    cy.wait('@update')
    cy.wait('@update')
    cy.get('@submit.all').should('have.length', 1)
    cy.get('@update.all').should('have.length', 2)
    cy.get('.el-message--success').should('contain', 'Peer evaluations updated successfully')
  })

  it('keeps a saved evaluation of a deactivated teammate editable', () => {
    teammates = [JOHN, teammate(5, 'Eric', false), JERRY]
    savedEvaluations = [saved(JOHN), saved(ERIC), saved(JERRY)]
    stubSaves()

    open()

    rowsShouldBe(['John', 'Eric', 'Jerry'])
    cy.contains('button', 'Update').click()
    cy.wait('@update')
    cy.get('.el-message--success').should('contain', 'Peer evaluations updated successfully')
    cy.get('@receipt.all').should('have.length', 0) // Nothing new was submitted
  })
})
