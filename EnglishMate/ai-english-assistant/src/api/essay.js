import request from '../utils/request'

export const checkEssay = data => request.post('/essay/check', data)
export const getEssayRecords = userId => request.get('/essay/records', { params: { userId } })
export const deleteEssayRecord = id => request.delete(`/essay/records/${id}`)
