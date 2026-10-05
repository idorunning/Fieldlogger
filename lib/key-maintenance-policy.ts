// Trusted server configuration identifies the existing operator account. Email
// addresses, account-created flags and missing configuration never grant access.
export function serviceKeyOwner(userId:string,config:{SHARED_OPENAI_KEY_OWNER_ID?:string;COMMUNITY_ADMIN_USER_ID?:string}) {
  return !!userId&&!!config.SHARED_OPENAI_KEY_OWNER_ID&&!!config.COMMUNITY_ADMIN_USER_ID&&
    userId===config.SHARED_OPENAI_KEY_OWNER_ID&&userId===config.COMMUNITY_ADMIN_USER_ID;
}
