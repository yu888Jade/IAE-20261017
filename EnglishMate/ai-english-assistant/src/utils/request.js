import axios from 'axios'
const request=axios.create({baseURL:import.meta.env.VITE_API_BASE_URL || '/api',timeout:120000})
request.interceptors.request.use(config=>{const token=localStorage.getItem('englishmate.token');if(token)config.headers.Authorization=`Bearer ${token}`;return config})
request.interceptors.response.use(r=>r,e=>{if(e.response?.status===401){localStorage.removeItem('englishmate.token');localStorage.removeItem('englishmate.user');if(location.pathname!='/login')location.href='/login'}return Promise.reject(e)})
export default request
