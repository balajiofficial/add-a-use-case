// UC-STU-deactivate-student: what the pages do with each answer. The student APIs are stubbed, so these tests check the
// screens and what they send; the backend's own tests cover who may deactivate and what deactivating does.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`. Logging in needs the real backend.

const student = (enabled) => ({
  id: 4,
  username: 'j.smith@abc.edu',
  enabled,
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
})

const found = (data) => ({ flag: true, code: 200, message: 'Find student successfully', data })

// A regular expression, because a glob such as **/students/4 would also match /evaluations/students/4
const STUDENT_URL = /\/api\/v1\/students\/4$/
const DEACTIVATE_URL = /\/api\/v1\/students\/4\/deactivate$/

describe('Deactivate a student from her details', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
  })

  function openDetails(enabled) {
    cy.intercept('GET', STUDENT_URL, { body: found(student(enabled)) }).as('student')
    cy.intercept('POST', DEACTIVATE_URL, {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: student(false)
      }
    }).as('deactivate')
    cy.visit('/students/4')
    cy.wait('@student')
  }

  it('warns of every consequence and sends nothing before confirming (step 4)', () => {
    openDetails(true)
    cy.get('[data-cy=student-status]').should('contain', 'Active')

    cy.get('[data-cy=student-deactivate]').click()

    cy.get('.el-message-box')
      .should('contain', 'Deactivate John Smith?')
      .and('contain', 'can no longer log in or submit weekly activity reports and peer evaluations')
      .and('contain', 'starting with their next request')
      .and('contain', 'the work they already submitted are kept')
      .and('contain', 'stay on their team')
      .and('contain', 'no longer owe weekly activity reports or peer evaluations')
      .and('contain', 'teammates can no longer submit a new peer evaluation of them')
      .and('contain', 'can be reactivated later')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('deactivates on confirming, alerts, and shows the new status (steps 5 and 6)', () => {
    openDetails(true)

    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.get('.el-message--success').should('contain', 'John Smith has been deactivated.')
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
  })

  it('offers nothing for a student who is already deactivated (extension 3a)', () => {
    openDetails(false)

    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
  })

  it('sends nothing and stays on her details when cancelled at the warning (extension 5a)', () => {
    openDetails(true)

    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    cy.location('pathname').should('eq', '/students/4')
    cy.get('[data-cy=student-status]').should('contain', 'Active')
    cy.get('[data-cy=student-deactivate]').should('be.visible')
  })

  it('alerts and shows the true status when someone else got there first (extension 6a)', () => {
    openDetails(true)
    cy.intercept('POST', DEACTIVATE_URL, {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'This student is already deactivated.' }
    })
    cy.intercept('GET', STUDENT_URL, { body: found(student(false)) }).as('reload')

    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@reload')
    cy.get('.el-message--error').should('contain', 'This student is already deactivated.')
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
  })
})

describe('The student edit form cannot deactivate', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
  })

  function openEditForm(enabled) {
    cy.intercept('POST', '**/students/search*', {
      body: {
        flag: true,
        code: 200,
        message: 'Find students successfully',
        data: { content: [student(enabled)], totalElements: 1 }
      }
    })
    cy.intercept('GET', '**/students/4', { body: found(student(enabled)) }).as('student')
    cy.visit('/students')
    cy.contains('.el-table__row', 'j.smith@abc.edu').find('button').click()
    cy.wait('@student')
    cy.get('.el-dialog').should('contain', 'Edit a student')
    cy.get('.el-dialog input:not([type=checkbox])').eq(2).should('have.value', 'John') // The form has finished loading
  }

  it('hides the status switch for an active student', () => {
    openEditForm(true)
    cy.get('[data-cy=student-status]').should('not.exist')
  })

  it('shows the status switch for a deactivated student', () => {
    openEditForm(false)
    cy.get('[data-cy=student-status]').should('be.visible')
  })
})
