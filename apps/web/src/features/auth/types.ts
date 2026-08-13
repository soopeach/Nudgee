export type AuthenticatedUser = {
  id: string
  displayName: string
  email: string
  photoURL: string | null
  isNewUser: boolean
}
