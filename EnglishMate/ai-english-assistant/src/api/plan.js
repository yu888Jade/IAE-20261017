import request from '../utils/request'
export const getStudyPlan=()=>request.get('/plan')
export const regenerateStudyPlan=()=>request.post('/plan/regenerate')
