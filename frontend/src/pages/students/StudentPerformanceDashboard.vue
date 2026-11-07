<template>
  <div class="student-header">
    <el-text class="mx-1" size="large">{{ studentName }}</el-text>
    <el-tag v-if="student" :type="student.enabled ? 'success' : 'danger'" data-cy="student-status">
      {{ student.enabled ? 'Active' : 'Deactivated' }}
    </el-tag>
    <el-button
      v-if="student?.enabled"
      type="danger"
      plain
      data-cy="student-deactivate"
      @click="deactivate"
    >
      Deactivate
    </el-button>
  </div>
  <StudentActivities :studentId="studentId" />
  <el-divider />
  <StudentEvaluations :studentId="studentId" />
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import StudentActivities from '../activities/admin/StudentActivities.vue'
import StudentEvaluations from '../evaluations/admin/StudentEvaluations.vue'

import { useRoute } from 'vue-router'
import type { AxiosError } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deactivateStudent, findStudentById } from '@/apis/student'
import type { FindStudentByIdResponse, Student } from '@/apis/student/types'

// The consequences in UC-STU-deactivate-student's Associated Information, condensed for the warning in step 4
const DEACTIVATION_CONSEQUENCES = [
  'They can no longer log in or submit weekly activity reports and peer evaluations, starting with their next request.',
  'Their account and the work they already submitted are kept.',
  'They stay on their team.',
  'They no longer owe weekly activity reports or peer evaluations, and teammates can no longer submit a new peer evaluation of them.',
  'The account can be reactivated later.'
]

const route = useRoute()

const studentId: number = Number(route.params.studentId)
const student = ref<Student>()
const studentName = computed(() =>
  student.value ? `${student.value.firstName} ${student.value.lastName}` : ''
)

async function loadStudent() {
  const result: FindStudentByIdResponse = await findStudentById(studentId)
  student.value = result.data
}

onMounted(loadStudent)

async function deactivate() {
  try {
    await ElMessageBox.confirm(
      h(
        'ul',
        { style: 'margin: 0; padding-left: 20px' },
        DEACTIVATION_CONSEQUENCES.map((consequence) => h('li', consequence))
      ),
      `Deactivate ${studentName.value}?`,
      { confirmButtonText: 'Deactivate', cancelButtonText: 'Cancel', type: 'warning' }
    )
  } catch {
    return // Changed her mind at the warning (extension 5a): nothing is sent and she stays on this page
  }

  try {
    const result = await deactivateStudent(studentId)
    student.value = result.data
    ElMessage.success(`${studentName.value} has been deactivated.`)
  } catch (error) {
    // Someone else deactivated this student after this page loaded (extension 6a). The request interceptor has already
    // shown the server's message, so all that is left is to show the true status.
    if ((error as AxiosError).response?.status === 409) {
      await loadStudent()
    }
  }
}
</script>

<style scoped>
.student-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
