import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toFoldableOps
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.{File, FileList}
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import cats.effect.IO
import doobie._
import doobie.implicits._
import cats.implicits._
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, MediaType, Uri}
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSConfig

import java.io.FileInputStream
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, Statement}
import java.util
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import play.api.libs.json.{Json, Writes}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Main application
object Main extends IOApp with Http4sDsl[IO] {

  // Configuration values
  val postgresUser: String = "postgres"
  val postgresPassword: String = "Euphie017119#"
  val postgresDB: String = "oauthgs"
  val postgresTable: String = "spreadsheet_data"
  val googleCredentialsPath: String = "src/main/scala/service-account-key.json"
  val clientId: String = "54656470738-p5cdt9lr2kmu1ut1enno6325dcnn3fi1.apps.googleusercontent.com"
  val clientSecret: String = "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"
  val redirectUri: String = "http://localhost:8080/oauth2callback"
  val scopes: List[String] = List("https://www.googleapis.com/auth/drive.readonly", "https://www.googleapis.com/auth/spreadsheets.readonly")

  // Initialize credentials from service account file (using OAuth2 service account)
  val serviceAccountKeyFilePath = googleCredentialsPath
  val credentials: GoogleCredentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountKeyFilePath))
    .createScoped(List("https://www.googleapis.com/auth/drive").asJava)
  val requestInitializer = new HttpCredentialsAdapter(credentials)

  // Google Drive API setup
  val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
  val driveService: Drive = new Drive.Builder(httpTransport, jsonFactory, requestInitializer)
    .setApplicationName("OAuthGS")
    .build()

  // Initialize OAuth Flow
  val flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientId, clientSecret, scopes.asJava)
    .setAccessType("offline")
    .build()

  // Function to Exchange Authorization Code for Access Token
  def exchangeCodeForTokens(authCode: String): IO[String] = IO {
    val tokenResponse = flow.newTokenRequest(authCode).setRedirectUri(redirectUri).execute()
    tokenResponse.getAccessToken
  }

  // Function to Initialize Google Drive Service with Access Token
  def getDriveService(accessToken: String): Drive = {
    val credentials = GoogleCredentials.create(new AccessToken(accessToken, null))
    val requestInitializer = new HttpCredentialsAdapter(credentials)
    new Drive.Builder(httpTransport, jsonFactory, requestInitializer)
      .setApplicationName("OAuthGS")
      .build()
  }

  // Function to Initialize Google Sheets Service with Access Token
  def getSheetsService(accessToken: String): Sheets = {
    val credentials = GoogleCredentials.create(new AccessToken(accessToken, null))
    val requestInitializer = new HttpCredentialsAdapter(credentials)
    new Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
      .setApplicationName("OAuthGS")
      .build()
  }


  case class SpreadsheetInfo(id: String, name: String)

  // Function to fetch all spreadsheets from the user's Google Drive
  def fetchSpreadsheets(accessToken: String): IO[List[SpreadsheetInfo]] = IO {
    val driveService = getDriveService(accessToken)
    val request = driveService.files().list().setQ("mimeType='application/vnd.google-apps.spreadsheet'")
    val result: FileList = request.execute()
    val spreadsheets: List[SpreadsheetInfo] = result.getFiles.asScala.toList.map(file =>
      SpreadsheetInfo(file.getId, file.getName)
    )
    spreadsheets
  }


  // Initialize Sheets API service
  val sheetsService: Sheets = new Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
    .setApplicationName("OAuthGS")
    .build()
  val conStr = s"jdbc:postgresql://localhost:5432/${postgresDB}?user=${postgresUser}&password=${postgresPassword}"

  // Function to check if a record exists in the database
  def recordExists(row: SpreadsheetData): Boolean = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    try {
      conn = DriverManager.getConnection(conStr)
      val query = s"SELECT COUNT(*) FROM ${postgresTable} WHERE col_1 = ? AND col_2 = ? AND col_3 = ?"
      stmt = conn.prepareStatement(query)
      stmt.setString(1, row.col_1)
      stmt.setString(2, row.col_2)
      stmt.setString(3, row.col_3)
      rs = stmt.executeQuery()
      rs.next() && rs.getInt(1) > 0 // If count > 0, record exists
    } catch {
      case e: Exception =>
        println(s"Error checking record existence: ${e.getMessage}")
        e.printStackTrace()
        false
    } finally {
      if (rs != null) rs.close()
      if (stmt != null) stmt.close()
      if (conn != null) conn.close()
    }
  }

  // Updated store function to check for duplicates before inserting
  def storeDataInDatabase(data: List[SpreadsheetData]): Unit = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    try {
      conn = DriverManager.getConnection(conStr)
      val insertQuery = s"INSERT INTO ${postgresTable} (col_1, col_2, col_3) VALUES (?, ?, ?)"
      stmt = conn.prepareStatement(insertQuery)
      for (row <- data) {
        if (!recordExists(row)) { // Check if the record already exists
          stmt.setString(1, row.col_1)
          stmt.setString(2, row.col_2)
          stmt.setString(3, row.col_3)
          stmt.addBatch()
        } else {
          println(s"Skipping duplicate row: $row")
        }
      }
      stmt.executeBatch()
      println("Data inserted successfully.")
    } catch {
      case e: Exception =>
        println(s"Error occurred while inserting data: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      if (stmt != null) stmt.close()
      if (conn != null) conn.close()
    }
  }

  // Function to fetch data from the spreadsheet and store it in the database
  def fetchSpreadsheetData(accessToken: String, spreadsheetId: String, range: String): IO[Option[List[util.List[AnyRef]]]] = IO {
    val sheetsService = getSheetsService(accessToken)
    val request: ValueRange = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val data = Option(request.getValues).map(_.asScala.toList)
    data.foreach { rows =>
      val spreadsheetData = rows.map { row =>
        SpreadsheetData(row.get(0).toString, row.get(1).toString, row.get(2).toString) // Adjust indices as per your columns
      }
      storeDataInDatabase(spreadsheetData) // Insert data into the database
    }
    data
  }

  // Case class to represent the spreadsheet data
  case class SpreadsheetData(col_1: String, col_2: String, col_3: String)

  // Create an implicit Writes instance for SpreadsheetInfo
  implicit val spreadsheetInfoWrites: Writes[SpreadsheetInfo] = Json.writes[SpreadsheetInfo]

  // Define your scopes
  val scope = "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/spreadsheets.readonly"
  // URL encode the scope
  val encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8.toString)

  // Routes for OAuth flow and fetching Google Sheets
  def authRoutes(implicit client: Client[IO]) = {
    HttpRoutes.of[IO] {
      // Route to initiate Google OAuth2 login
      case GET -> Root / "auth" / "google" =>
        val authorizationUrl = s"https://accounts.google.com/o/oauth2/auth?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=$encodedScope"
        Found(Uri.unsafeFromString(authorizationUrl))

      // Route to Handle OAuth Callback and Exchange Code for Tokens
      case GET -> Root / "oauth2callback" :? CodeQueryParamMatcher(code) =>
        for {
          accessToken <- exchangeCodeForTokens(code) // Get the access token
          redirectUri = Uri.unsafeFromString(s"http://localhost:3000/dashboard?accessToken=$accessToken") // Construct the URI safely
          response <- Found(redirectUri) // Redirect to frontend with the token
        } yield response

      // Route to fetch Google Spreadsheets
      case GET -> Root / "spreadsheets" / accessToken =>
        for {
          spreadsheets <- fetchSpreadsheets(accessToken) // Fetch the spreadsheets
          response <- {
            if (spreadsheets.nonEmpty) {
              // Create a JSON-like string for the spreadsheet data
              val jsonResponse = s"""{"spreadsheets":[${spreadsheets.map(file => s"""{"id":"${file.id}","name":"${file.name}"}""").mkString(",")}] }"""
              Ok(jsonResponse, `Content-Type`(MediaType.application.json)) // Return the JSON response directly
            } else {
              NotFound("No spreadsheets found.")
            }
          }
        } yield response

      // Route to fetch data from a specific spreadsheet
      case GET -> Root / "spreadsheet" / accessToken / id / "data" =>
        for {
          dataOpt <- fetchSpreadsheetData(accessToken, id, "Sheet1!A1:C4") // Specify the range as needed
          response <- dataOpt match {
            case Some(data) => Ok(s"Data from spreadsheet $id: ${data.map(_.mkString(", ")).mkString("\n")}\n")
            case None => NotFound(s"No data found in spreadsheet $id.")
          }
        } yield response
    }
  }

  // CORS Configuration
  val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = true,
    allowedOrigins = Set("*"), // You can specify specific origins instead of "*"
    allowCredentials = false, // Set to false if you do not need credentials
    maxAge = 1.day.toSeconds
  )

  // Query Parameter Matcher for Authorization Code
  object CodeQueryParamMatcher extends QueryParamDecoderMatcher[String]("code")

  // Server start-up logic
  override def run(args: List[String]): IO[ExitCode] = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      val routes = authRoutes(client)
      val corsRoutes = CORS(routes, corsConfig)
      BlazeServerBuilder[IO](global)
        .bindHttp(8080, "localhost")
        .withHttpApp(corsRoutes.orNotFound)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }
  }
}