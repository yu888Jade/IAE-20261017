import request from '../utils/request'
export const getProfile=()=>request.get('/profile')
export const saveProfile=data=>request.post('/profile',data)
