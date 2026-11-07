// UC-EVA-submit-evaluation: scores must be integers. The roster and evaluation APIs are stubbed, so this checks what the
// form lets through; EvaluationIntegrationTest and RatingTest cover the server's own refusal.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`. Logging in needs the real backend.

const ok = (data) => ({ flag: true, code: 200, message: 'ok', data })

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  enabled: true,
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  teamId: 1,
  sectionId: 2
}

describe('Peer evaluation scores are whole numbers', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', /\/api\/v1\/students\/teams\/1$/, { body: ok([john]) })
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', { body: ok([]) })
    cy.intercept('POST', /\/api\/v1\/evaluations$/, (req) => req.reply(ok({ ...req.body, evaluationId: 1 }))).as('submit')
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', { body: ok(null) })
    cy.visit('/evaluations/submit-evaluations')
    cy.get('.el-input-number input').first().should('have.value', '10')
  })

  it('turns a typed fraction into a whole number and submits only whole numbers', () => {
    cy.get('.el-input-number input').first().clear().type('7.5').blur()
    cy.get('.el-input-number input').first().should('have.value', '8')

    cy.contains('button', 'Submit').click()
    cy.get('[data-cy=evaluation-review-confirm]').click()

    cy.wait('@submit').then(({ request }) => {
      const scores = request.body.ratings.map((rating) => rating.actualScore)
      expect(scores.every(Number.isInteger), `scores ${scores}`).to.equal(true)
      expect(scores).to.include(8)
    })
  })
})
