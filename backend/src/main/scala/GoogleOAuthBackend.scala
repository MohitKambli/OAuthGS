import cats.effect.{ExitCode, IO, IOApp}
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.{File, FileList}
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, MediaType, Uri}
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
  val clientId: String = "54656470738-p5cdt9lr2kmu1ut1enno6325dcnn3fi1.apps.googleusercontent.com"
  val clientSecret: String = "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"
  val redirectUri: String = "http://localhost:8080/oauth2callback"
  val scopes: List[String] = List("https://www.googleapis.com/auth/drive.readonly", "https://www.googleapis.com/auth/spreadsheets.readonly")

  // Google Drive API setup
  val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

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

  val conStr = s"jdbc:postgresql://localhost:5432/${postgresDB}?user=${postgresUser}&password=${postgresPassword}"
  // Function to dynamically create a table based on the header row
  def createTable(headers: List[String], tableName: String): Unit = {
    var conn: Connection = null
    var stmt: Statement = null
    try {
      conn = DriverManager.getConnection(conStr)
      stmt = conn.createStatement()
      // Create SQL query to create table with dynamic columns
      val columns = headers.map(header => s""""$header" TEXT""").mkString(", ")
      val createTableQuery = s"""CREATE TABLE IF NOT EXISTS "$tableName" ($columns);"""
      stmt.executeUpdate(createTableQuery)
      println(s"Table $tableName created successfully with columns: $columns")
    } catch {
      case e: Exception =>
        println(s"Error creating table: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      if (stmt != null) stmt.close()
      if (conn != null) conn.close()
    }
  }

  // Function to insert data dynamically into the table
  def insertDataIntoTable(data: List[SpreadsheetData], tableName: String): Unit = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    try {
      conn = DriverManager.getConnection(conStr)
      // Assuming all rows have the same columns (headers), get the first row's columns for dynamic query generation
      if (data.nonEmpty) {
        val firstRowColumns = data.head.columns.keys.toList
        // Quote the column names properly to handle case-sensitivity in PostgreSQL
        val columns = firstRowColumns.map(col => s""""$col"""").mkString(", ")
        val placeholders = firstRowColumns.map(_ => "?").mkString(", ")
        // Generate the dynamic SQL for inserting data
        val insertQuery = s"""INSERT INTO "$tableName" ($columns) VALUES ($placeholders);"""
        stmt = conn.prepareStatement(insertQuery)
        // Iterate through each row and add it to the batch
        data.foreach { row =>
          row.columns.values.zipWithIndex.foreach { case (value, index) =>
            stmt.setString(index + 1, value) // Dynamically bind values
          }
          stmt.addBatch() // Add to batch
        }
        // Execute batch insertion
        stmt.executeBatch()
        println(s"Data inserted successfully into $tableName.")
      } else {
        println("No data to insert.")
      }
    } catch {
      case e: Exception =>
        println(s"Error inserting data: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      if (stmt != null) stmt.close()
      if (conn != null) conn.close()
    }
  }

  // Function to fetch spreadsheet data and store it in the database dynamically
  def fetchSpreadsheetData(accessToken: String, spreadsheetId: String, range: String): IO[Option[List[util.List[AnyRef]]]] = IO {
    val sheetsService = getSheetsService(accessToken)
    val request: ValueRange = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
    val data = Option(request.getValues).map(_.asScala.toList)

    data.foreach { rows =>
      // Assume the first row is the header (column names)
      val headers = rows.head.map(_.toString).toList
      println(s"Headers: ${headers}")
      // Create the table dynamically based on the headers
      createTable(headers, spreadsheetId)
      // Process the remaining rows (data)
      val spreadsheetData = rows.tail.map { row =>
        // Create a map of column name to value
        val rowMap = headers.zip(row.map(_.toString)).toMap
        SpreadsheetData(rowMap)
      }
      // Insert data into the dynamically created table
      insertDataIntoTable(spreadsheetData, spreadsheetId)
    }
    data
  }

  // Case class to represent the spreadsheet data
  case class SpreadsheetData(columns: Map[String, String])

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
          dataOpt <- fetchSpreadsheetData(accessToken, id, "Sheet1") // Specify the range as needed
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
