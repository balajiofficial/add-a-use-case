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
    cy.get('.el-dialog input').eq(2).should('have.value', 'John') // The form has finished loading
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
