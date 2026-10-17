import request from '../utils/request'

export const getMistakes = userId => request.get('/mistakes', { params: { userId } })
export const addMistake = data => request.post('/mistakes', data)
export const setMistakeMastered = (id, value) => request.put(`/mistakes/${id}/mastered`, null, { params: { value } })
export const deleteMistake = id => request.delete(`/mistakes/${id}`)
