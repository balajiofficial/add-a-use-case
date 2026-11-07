<template>
  <el-card class="page-container">
    <template #header>
      <div class="header">
        <span
          >Submit Peer Evaluations for week:
          <el-tag type="primary" style="font-size: 14px; font-weight: bold">{{
            previousWeekRange
          }}</el-tag></span
        >
      </div>
      <div style="margin-top: 10px">
        <span>
          The default score for each criterion is 10. You can change it to a value between 0 and 10.
        </span>
        <!-- Remind the user that "You have NOT submitted the evaluation" -->
        <el-tag v-if="isNewSubmission" type="danger" style="font-size: 14px; font-weight: bold"
          >You have NOT submitted the evaluation. Click the "Submit" button.</el-tag
        >
      </div>
    </template>
    <!-- Evaluation Table, adding height can fix the table header -->
    <el-table
      :data="peerEvalutions"
      style="width: 100%"
      stripe
      border
      height="600"
      v-loading="loading"
      scrollbar-always-on
    >
      <el-table-column
        label="Student Name"
        prop="evaluateeName"
        min-width="100"
        fixed
      ></el-table-column>
      <el-table-column v-for="criterion in criteria" :key="criterion.criterionId" min-width="150">
        <template #header>
          <el-tooltip effect="dark" :content="criterion.criterion" placement="top">
            <span>{{ criterion.description }}</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <div style="display: flex; justify-content: center; align-items: center">
            <el-input-number
              v-model="
                row.ratings.find((r: Rating) => r.criterionId === criterion.criterionId).actualScore
              "
              :min="0"
              :max="criterion.maxScore"
              :step="1"
              step-strictly
              controls-position="right"
              style="width: 80px"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Public comments are available to the evaluatee."
            placement="top"
          >
            <span>Public Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.publicComment"
            type="textarea"
            :rows="5"
            placeholder="Enter public comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Private comments are ONLY available to the instructor."
            placement="top"
          >
            <span>Private Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.privateComment"
            type="textarea"
            :rows="5"
            placeholder="Enter private comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No data is available." />
      </template>
    </el-table>
    <!-- submit button on the right side-->
    <div style="display: flex; justify-content: flex-end">
      <el-button
        type="primary"
        @click="reviewPeerEvaluations"
        v-if="isNewSubmission"
        :loading="isLoading"
        >Submit</el-button
      >
      <el-button type="warning" @click="reviewPeerEvaluations" :loading="isLoading" v-else
        >Update</el-button
      >
    </div>
    <!-- UC-EVA-submit-evaluation steps 6 to 8: nothing is sent until the student confirms what she entered -->
    <el-dialog
      v-model="isReviewing"
      title="Review your peer evaluations"
      width="90%"
      data-cy="evaluation-review"
    >
      <el-table :data="peerEvalutions" style="width: 100%" border max-height="50vh">
        <el-table-column label="Student Name" prop="evaluateeName" min-width="110" fixed />
        <el-table-column
          v-for="criterion in criteria"
          :key="criterion.criterionId"
          :label="criterion.description"
          min-width="105"
        >
          <template #default="{ row }">{{ scoreFor(row, criterion) }}</template>
        </el-table-column>
        <el-table-column label="Total" min-width="70">
          <template #default="{ row }">{{ totalFor(row) }}</template>
        </el-table-column>
        <el-table-column label="Public Comment" prop="publicComment" min-width="150" />
        <el-table-column label="Private Comment" prop="privateComment" min-width="150" />
      </el-table>
      <p data-cy="evaluation-review-note">
        You can update your peer evaluations and submit them again until the submission window for
        this week closes.
      </p>
      <template #footer>
        <el-button data-cy="evaluation-review-modify" @click="isReviewing = false"
          >Modify</el-button
        >
        <el-button
          type="primary"
          data-cy="evaluation-review-confirm"
          @click="confirmPeerEvaluations"
        >
          {{ isNewSubmission ? 'Confirm and submit' : 'Confirm and update' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { findStudentsByTeamId } from '@/apis/student'
import type { Student, FindStudentByTeamIdResponse } from '@/apis/student/types'
import { findSectionById } from '@/apis/section'
import { findRubricById } from '@/apis/rubric'
import type { FindRubricByIdResponse, Criterion } from '@/apis/rubric/types'
import { getPreviousWeek, getPreviousWeekRange } from '@/utils/week'
import { useUserInfoStore } from '@/stores/userInfo'
import type { FindSectionByIdResponse } from '@/apis/section/types'
import {
  submitEvaluation,
  getEvaluationsByEvaluatorIdAndWeek,
  updateEvaluation,
  sendPeerEvaluationSubmissionConfirmationEmail
} from '@/apis/evaluation'
import { ElMessage } from 'element-plus'
import type { FetchPeerEvaluationsResponse, PeerEvaluation, Rating } from '@/apis/evaluation/types'

const previousWeek = ref<string>('')
const previousWeekRange = ref<string>('')

const peerEvalutions = ref<PeerEvaluation[]>()

const loading = ref<boolean>(false)

// A new submission until something has been saved for this week; an update from then on
const isNewSubmission = computed(() => !peerEvalutions.value?.some((row) => row.evaluationId))

const teamId = ref<number>(NaN) // Team ID of the student

const students = ref<Student[]>([]) // Students on the same team
const sectionId = ref<number>() // Section ID of the team
const criteria = ref<Criterion[]>([]) // Criteria used for peer evaluations
const isLoading = ref(false)
const isReviewing = ref(false) // Whether the review dialog of steps 6 to 8 is open

const userInfoStore = useUserInfoStore() // Need team ID to search for students

// Load data when the component is mounted
onMounted(async () => {
  previousWeek.value = getPreviousWeek()
  previousWeekRange.value = getPreviousWeekRange()
  teamId.value = (userInfoStore.userInfo as Student).teamId as number
  if (!teamId.value) {
    ElMessage.error('You have not been assigned a team yet.')
    return
  }
  await loadStudents()
  sectionId.value = (userInfoStore.userInfo as Student).sectionId
  await loadCriteria()
  loading.value = true
  await prepareStartingPeerEvaluations()
  loading.value = false
})

// Load activities based on the search criteria
async function loadStudents() {
  // On the back end, the page number starts from 0, so subtract 1 here
  const result: FindStudentByTeamIdResponse = await findStudentsByTeamId(teamId.value)
  students.value = result.data
}

// Load criteria information
async function loadCriteria() {
  const sectionResult: FindSectionByIdResponse = await findSectionById(sectionId.value as number)
  const rubricResult: FindRubricByIdResponse = await findRubricById(
    sectionResult.data.rubricId as number
  )
  criteria.value = rubricResult.data.criteria!
}

// The rows are always what has been saved for the week plus a blank row for each active teammate with none, so a saved
// evaluation of a deactivated teammate stays editable (BR-evaluation-active-evaluatee) and the student can still submit
// whatever she owes (BR-submission-owed).
async function prepareStartingPeerEvaluations() {
  const result: FetchPeerEvaluationsResponse = await getEvaluationsByEvaluatorIdAndWeek(
    (userInfoStore.userInfo as Student).id as number,
    previousWeek.value
  )

  const evaluateeIds = new Set(result.data.map((evaluation) => evaluation.evaluateeId))
  const blankRows: PeerEvaluation[] = students.value
    .filter((student) => student.enabled && !evaluateeIds.has(student.id as number))
    .map((student) => ({
      week: previousWeek.value,
      evaluatorId: (userInfoStore.userInfo as Student).id as number,
      evaluateeId: student.id as number,
      evaluateeName: `${student.firstName} ${student.lastName}`,
      ratings: criteria.value.map((criterion) => ({
        criterionId: criterion.criterionId as number,
        criterion: criterion.criterion,
        actualScore: 10 // Default score, can be changed by the user
      })),
      publicComment: '',
      privateComment: ''
    }))

  peerEvalutions.value = [...result.data, ...blankRows]
}

function scoreFor(row: PeerEvaluation, criterion: Criterion) {
  return row.ratings.find((rating) => rating.criterionId === criterion.criterionId)?.actualScore
}

function totalFor(row: PeerEvaluation) {
  return row.ratings.reduce((total, rating) => total + rating.actualScore, 0)
}

// Step 5 and extension 4a: a score field the student cleared holds no value, so she is told which one before the review.
// A score cannot otherwise be invalid here, since the field keeps it a whole number within the criterion's range.
function reviewPeerEvaluations() {
  const rows = peerEvalutions.value
  if (!rows?.length) return

  for (const row of rows) {
    for (const criterion of criteria.value) {
      if (scoreFor(row, criterion) == null) {
        ElMessage.error(`Enter a score for ${row.evaluateeName}: ${criterion.description}`)
        return
      }
    }
  }
  isReviewing.value = true
}

async function confirmPeerEvaluations() {
  isReviewing.value = false
  await savePeerEvaluations()
}

// Saves every row: a saved row is updated and a blank one is submitted. It waits for all of them to settle, because a
// teammate may have been deactivated since this page loaded (UC-EVA-submit-evaluation extension 9a) and the rest must still
// finish before the page shows what was saved. The request interceptor has already shown the server's reason for a refusal.
async function savePeerEvaluations() {
  const rows = peerEvalutions.value
  if (!rows) return

  const wasNewSubmission = isNewSubmission.value
  isLoading.value = true
  let submittedAny = false
  try {
    const results = await Promise.allSettled(
      rows.map((row) => (row.evaluationId ? updateEvaluation(row) : submitEvaluation(row)))
    )
    submittedAny = results.some(
      (result, index) => result.status === 'fulfilled' && !rows[index]?.evaluationId
    )

    const saved = results.flatMap((result) =>
      result.status === 'fulfilled' ? [result.value.data] : []
    )
    if (saved.length === rows.length) {
      peerEvalutions.value = saved
      ElMessage.success(
        `Peer evaluations ${wasNewSubmission ? 'submitted' : 'updated'} successfully`
      )
    } else {
      await loadStudents()
      await prepareStartingPeerEvaluations()
      ElMessage.warning(
        'Some peer evaluations were not saved. This page now shows the ones that were.'
      )
    }
  } finally {
    isLoading.value = false
  }

  // Send a submission confirmation email to the submitter
  if (submittedAny) {
    await sendPeerEvaluationSubmissionConfirmationEmail(previousWeekRange.value)
    ElMessage.success('A confirmation email has been sent to you.')
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100%;
  box-sizing: border-box;
  .header {
    display: flex;
    align-items: center;
    gap: 20px;
  }
}
</style>
