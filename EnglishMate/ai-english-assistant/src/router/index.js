import {createRouter,createWebHistory} from 'vue-router'
import HomeView from '../views/HomeView.vue'
const router=createRouter({history:createWebHistory(import.meta.env.BASE_URL),routes:[
 {path:'/login',name:'login',component:()=>import('../views/LoginView.vue'),meta:{public:true}},
 {path:'/',name:'home',component:HomeView},{path:'/chat',name:'chat',component:()=>import('../views/ChatView.vue')},{path:'/essay',name:'essay',component:()=>import('../views/EssayView.vue')},{path:'/mistakes',name:'mistakes',component:()=>import('../views/MistakeView.vue')},{path:'/plan',name:'plan',component:()=>import('../views/PlanView.vue')},{path:'/profile',name:'profile',component:()=>import('../views/ProfileView.vue')},{path:'/dashboard',name:'dashboard',component:()=>import('../views/DashboardView.vue')}
],scrollBehavior(){return{top:0}}})
router.beforeEach(to=>{const token=localStorage.getItem('englishmate.token');if(!to.meta.public&&!token)return'/login';if(to.path==='/login'&&token)return'/';})
export default router
