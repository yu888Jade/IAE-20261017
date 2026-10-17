import request from '../utils/request'
export const login=data=>request.post('/auth/login',data)
export const register=data=>request.post('/auth/register',data)
export const me=()=>request.get('/auth/me')
