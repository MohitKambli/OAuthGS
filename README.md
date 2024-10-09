### OAuth & PostgreSQL Configuration

Following variables in the GoogleOAuthBackend.scala file need to be filled according to the developer's credentials

```

val postgresUser: String = ""

val postgresPassword: String = ""

val postgresDB: String = ""

val googleCredentialsPath: String = ""

val clientId: String = ""

val clientSecret: String = ""

val redirectUri: String = ""

```

The OAuth related details need to be fetched from the project created on Google's Cloud Project

Google's Drive API and Sheets API need to be enabled for the created project

The Service Account Key, present in json format, needs to be downloaded from the cloud project, placed in the backend project and 'googleCredentialsPath' variable needs to be updated with its appropriate path

