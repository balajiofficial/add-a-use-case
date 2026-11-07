// UC-EVA-submit-evaluation steps 5 to 9 and extension 4a: the student reviews what she entered before anything is sent.
// The roster and evaluation APIs are stubbed, so these tests check the screens and what they send.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`. Logging in needs the real backend.
// Seed data: John (id 4) is on Team1 with Eric (5), and the course section's rubric has criteria 1 to 6.

const ok = (data) => ({ flag: true, code: 200, message: 'ok', data })

const teammate = (id, firstName) => ({
  id,
  username: `${firstName}@abc.edu`,
  enabled: true,
  firstName,
  lastName: 'Teammate',
  email: `${firstName}@abc.edu`,
  teamId: 1,
  sectionId: 2
})

const JOHN = teammate(4, 'John')
const ERIC = teammate(5, 'Eric')

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
    actualScore: 9
  })),
  totalScore: 54,
  publicComment: 'Saved public comment',
  privateComment: 'Saved private comment'
})

const FORM_ROWS = '.page-container > .el-card__body > .el-table .el-table__row'
const REVIEW = '[data-cy=evaluation-review]'
const CONFIRM = '[data-cy=evaluation-review-confirm]'
const MODIFY = '[data-cy=evaluation-review-modify]'

describe('Review peer evaluations before they are saved', () => {
  let savedEvaluations

  beforeEach(() => {
    savedEvaluations = []
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', /\/api\/v1\/students\/teams\/1$/, { body: ok([JOHN, ERIC]) })
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', (req) => req.reply(ok(savedEvaluations)))
    cy.intercept('POST', /\/api\/v1\/evaluations$/, (req) =>
      req.reply(ok({ ...req.body, evaluationId: 100 + req.body.evaluateeId }))
    ).as('submit')
    cy.intercept('PUT', '**/evaluations/*', (req) => req.reply(ok(req.body))).as('update')
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', { body: ok(null) }).as('receipt')
  })

  function open() {
    cy.visit('/evaluations/submit-evaluations')
    cy.get(FORM_ROWS).should('have.length', 2)
  }

  // The row's first score field, and its public and private comments
  const firstScore = () => cy.get(FORM_ROWS).first().find('.el-input-number input').first()
  const comments = () => cy.get(FORM_ROWS).first().find('textarea')

  function fillInJohnsRow(score, publicComment, privateComment) {
    firstScore().clear().type(score).blur()
    comments().eq(0).clear().type(publicComment)
    comments().eq(1).clear().type(privateComment)
  }

  it('displays the details and sends nothing until confirmed (steps 6 and 7)', () => {
    open()
    fillInJohnsRow('7', 'Good work', 'For the instructor')

    cy.contains('button', 'Submit').click()

    cy.get(REVIEW)
      .should('be.visible')
      .and('contain', 'John Teammate')
      .and('contain', 'Eric Teammate')
      .and('contain', 'Good work')
      .and('contain', 'For the instructor')
    cy.get(REVIEW).find('.el-table__row').first().should('contain', '7').and('contain', '57') // 7 + five 10s
    cy.get('[data-cy=evaluation-review-note]')
      .should('contain', 'update your peer evaluations')
      .and('contain', 'until the submission window for this week closes')
    cy.get(CONFIRM).should('contain', 'Confirm and submit')
    cy.get('@submit.all').should('have.length', 0)
  })

  it('returns to the form with everything kept when she chooses to modify (step 8)', () => {
    open()
    fillInJohnsRow('7', 'First draft', 'Private draft')

    cy.contains('button', 'Submit').click()
    cy.get(MODIFY).click()

    cy.get(REVIEW).should('not.be.visible')
    cy.get('@submit.all').should('have.length', 0)
    firstScore().should('have.value', '7')
    comments().eq(0).should('have.value', 'First draft')
    comments().eq(1).should('have.value', 'Private draft')
    cy.contains('button', 'Submit').scrollIntoView().should('be.visible')
  })

  it('sends what she changed after modifying, and tells her it was submitted (steps 8 and 9)', () => {
    open()
    fillInJohnsRow('7', 'First draft', 'Private draft')
    cy.contains('button', 'Submit').click()
    cy.get(MODIFY).click()
    cy.get(REVIEW).should('not.be.visible')
    fillInJohnsRow('6', 'Second draft', 'Private draft')

    cy.contains('button', 'Submit').click()
    cy.get(REVIEW).find('.el-table__row').first().should('contain', 'Second draft').and('contain', '56')
    cy.get(CONFIRM).click()

    cy.wait('@submit')
    cy.wait('@submit')
    cy.get('@submit.all').should('have.length', 2)
    cy.get('@submit.all').then((calls) => {
      const johns = calls.map((call) => call.request.body).find((body) => body.evaluateeId === JOHN.id)
      expect(johns.publicComment).to.equal('Second draft')
      expect(johns.ratings[0].actualScore).to.equal(6)
    })
    cy.get('.el-message--success').should('contain', 'Peer evaluations submitted successfully')
    cy.wait('@receipt')
    cy.get(REVIEW).should('not.be.visible')
    cy.contains('button', 'Update').scrollIntoView().should('be.visible')
  })

  it('asks for confirmation when re-submitting too, and updates in place (step 7)', () => {
    savedEvaluations = [saved(JOHN), saved(ERIC)]
    open()
    firstScore().should('have.value', '9')

    cy.contains('button', 'Update').click()

    cy.get(REVIEW).should('be.visible').and('contain', 'Saved public comment').and('contain', '54')
    cy.get(CONFIRM).should('contain', 'Confirm and update')
    cy.get('@update.all').should('have.length', 0)
    cy.get(CONFIRM).click()

    cy.wait('@update')
    cy.wait('@update')
    cy.get('.el-message--success').should('contain', 'Peer evaluations updated successfully')
    cy.get('@submit.all').should('have.length', 0)
    cy.get('@receipt.all').should('have.length', 0)
  })

  it('submits once when the confirm button is double-clicked', () => {
    open()

    cy.contains('button', 'Submit').click()
    cy.get(CONFIRM).dblclick()

    cy.get('.el-message--success').should('contain', 'Peer evaluations submitted successfully')
    cy.wait('@receipt')
    cy.get('@submit.all').should('have.length', 2) // One per teammate
    cy.get('@receipt.all').should('have.length', 1)
  })

  it('stops a comment at the 255 characters the server accepts (step 5)', () => {
    open()

    comments().eq(0).invoke('attr', 'maxlength').should('eq', '255')
    comments().eq(1).invoke('attr', 'maxlength').should('eq', '255')
    cy.get(FORM_ROWS).first().find('.el-input__count, .el-textarea__count').should('have.length', 2)
  })

  it('names the missing score and shows no review when a score is blank (step 5, extension 4a)', () => {
    open()
    firstScore().clear().blur()

    cy.contains('button', 'Submit').click()

    cy.get('.el-message--error').should('contain', 'Enter a score for John Teammate')
    cy.get(REVIEW).should('not.exist')
    cy.get('@submit.all').should('have.length', 0)
  })
})
