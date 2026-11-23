using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using RabbitMQ.Client;

var builder = WebApplication.CreateBuilder(args);

string Requerida(string nombre) =>
    Environment.GetEnvironmentVariable(nombre)
    ?? throw new InvalidOperationException($"Falta la variable de entorno {nombre}");

var amqpUrl = Requerida("AMQP_URL");                              // amqps://usuario:clave@host/vhost
var supabaseUrl = Requerida("SUPABASE_URL").TrimEnd('/');         // https://TU_PROJECT_ID.supabase.co
var supabaseKey = Requerida("SUPABASE_PUBLISHABLE_KEY");          // sb_publishable_...
var queueName = Environment.GetEnvironmentVariable("QUEUE_NAME") ?? "reportes";

builder.WebHost.ConfigureKestrel(o => o.Limits.MaxRequestBodySize = 64 * 1024); // 64 KB: los archivos NO pasan por aquí
builder.Services.AddHttpClient("supabase", c => c.Timeout = TimeSpan.FromSeconds(10));
builder.Services.AddSingleton(new Broker(amqpUrl, queueName));

var app = builder.Build();

// Cloud Run y tus pruebas: comprobar que el servicio está vivo
app.MapGet("/", () => Results.Ok(new { servicio = "ingesta", estado = "ok" }));

// ===========================================================================
// POST /reportes : valida el token, valida el reporte y lo publica en la cola
// ===========================================================================
app.MapPost("/reportes", async (
    HttpRequest request,
    ReporteRequest body,
    IHttpClientFactory httpFactory,
    Broker broker,
    ILogger<Program> log) =>
{
    // 1. Token de la app
    var cabecera = request.Headers.Authorization.ToString();
    if (!cabecera.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        return Results.Unauthorized();
    var token = cabecera["Bearer ".Length..].Trim();

    // 2. Validar el token con Supabase Auth y sacar el id del usuario
    string? authId;
    try
    {
        var http = httpFactory.CreateClient("supabase");
        using var req = new HttpRequestMessage(HttpMethod.Get, $"{supabaseUrl}/auth/v1/user");
        req.Headers.Add("apikey", supabaseKey);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        using var resp = await http.SendAsync(req);

        var codigo = (int)resp.StatusCode;
        if (codigo is >= 400 and < 500) return Results.Unauthorized();
        if (!resp.IsSuccessStatusCode)
        {
            log.LogError("Supabase Auth respondió {Codigo}", codigo);
            return Results.StatusCode(502);
        }

        using var doc = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
        authId = doc.RootElement.GetProperty("id").GetString();
    }
    catch (Exception e) when (e is HttpRequestException or TaskCanceledException or JsonException or KeyNotFoundException)
    {
        log.LogError(e, "No se pudo validar el token con Supabase");
        return Results.StatusCode(502);
    }
    if (string.IsNullOrEmpty(authId)) return Results.Unauthorized();

    // 3. Validar el contenido del reporte
    var errores = body.Validar();
    if (errores.Count > 0) return Results.BadRequest(new { errores });

    // 4. Armar el mensaje. La identidad sale del token, NO de lo que mande la app
    var reportId = Guid.NewGuid().ToString();
    var mensaje = new
    {
        report_id = reportId,
        auth_id = authId,
        name = body.Name!.Trim(),
        description = body.Description?.Trim(),      // opcional: puede venir vacío
        service_type = body.ServiceType!.Trim(),
        latitude = body.Latitude,
        longitude = body.Longitude,
        municipality = body.Municipality?.Trim(),
        department = body.Department?.Trim(),
        dane_code = body.DaneCode,
        date_time = body.FechaHora
    };

    // 5. Publicar en la cola (espera la confirmación de RabbitMQ)
    try
    {
        await broker.PublicarAsync(JsonSerializer.SerializeToUtf8Bytes(mensaje));
    }
    catch (Exception e)
    {
        log.LogError(e, "No se pudo publicar en la cola");
        return Results.StatusCode(503);
    }

    return Results.Accepted(value: new { report_id = reportId });
});

// ===========================================================================
// POST /reportes/{id}/validar : marca un reporte como validado.
// La regla vive en la función validar_reporte() de Supabase: ahí se comprueba
// que el usuario exista y que no esté validando su propio reporte.
// ===========================================================================
app.MapPost("/reportes/{id:long}/validar", async (
    long id,
    HttpRequest request,
    IHttpClientFactory httpFactory,
    ILogger<Program> log) =>
{
    var cabecera = request.Headers.Authorization.ToString();
    if (!cabecera.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        return Results.Unauthorized();
    var token = cabecera["Bearer ".Length..].Trim();

    try
    {
        var http = httpFactory.CreateClient("supabase");
        using var req = new HttpRequestMessage(HttpMethod.Post, $"{supabaseUrl}/rest/v1/rpc/validar_reporte");
        req.Headers.Add("apikey", supabaseKey);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        req.Content = new StringContent(
            JsonSerializer.Serialize(new { p_id_falla = id }), Encoding.UTF8, "application/json");

        using var resp = await http.SendAsync(req);
        var contenido = (await resp.Content.ReadAsStringAsync()).Trim();

        if (resp.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            return Results.Unauthorized();

        if (!resp.IsSuccessStatusCode)
        {
            // Los errores de la función (por ejemplo, validar tu propio reporte) llegan como 400
            log.LogWarning("validar_reporte respondió {Codigo}: {Contenido}", (int)resp.StatusCode, contenido);
            return resp.StatusCode == HttpStatusCode.BadRequest
                ? Results.BadRequest(new { error = "No se pudo validar el reporte" })
                : Results.StatusCode(502);
        }

        return contenido == "true"
            ? Results.Ok(new { mensaje = "Reporte validado exitosamente" })
            : Results.NotFound(new { error = "El reporte no existe" });
    }
    catch (Exception e) when (e is HttpRequestException or TaskCanceledException)
    {
        log.LogError(e, "No se pudo conectar con Supabase para validar el reporte");
        return Results.StatusCode(502);
    }
});

app.Run();

// ---------------------------------------------------------------------------

record ReporteRequest(
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("description")] string? Description,
    [property: JsonPropertyName("service_type")] string? ServiceType,
    [property: JsonPropertyName("latitude")] double? Latitude,
    [property: JsonPropertyName("longitude")] double? Longitude,
    [property: JsonPropertyName("municipality")] string? Municipality,
    [property: JsonPropertyName("department")] string? Department,
    [property: JsonPropertyName("dane_code")] string? DaneCode,
    [property: JsonPropertyName("date_time")] string? FechaHora)
{
    public List<string> Validar()
    {
        var e = new List<string>();
        if (string.IsNullOrWhiteSpace(Name) || Name.Length > 100)
            e.Add("name es obligatorio (máx. 100 caracteres)");
        if (Description?.Length > 1000)
            e.Add("description admite máx. 1000 caracteres");
        if (string.IsNullOrWhiteSpace(ServiceType) || ServiceType.Length > 50)
            e.Add("service_type es obligatorio (máx. 50 caracteres)");
        if (Latitude is < -90 or > 90)
            e.Add("latitude debe estar entre -90 y 90");
        if (Longitude is < -180 or > 180)
            e.Add("longitude debe estar entre -180 y 180");
        if (!DateTimeOffset.TryParse(FechaHora, out _))
            e.Add("date_time debe ser una fecha en formato ISO-8601");
        if (DaneCode is not null && (DaneCode.Length > 8 || !DaneCode.All(char.IsAsciiDigit)))
            e.Add("dane_code debe ser numérico (máx. 8 dígitos)");
        if (Municipality?.Length > 100 || Department?.Length > 100)
            e.Add("municipality y department admiten máx. 100 caracteres");
        return e;
    }
}

// Mantiene una conexión a RabbitMQ y la vuelve a crear si se cae
// (en Cloud Run la conexión puede quedar inactiva entre peticiones).
class Broker(string amqpUrl, string queueName)
{
    private readonly SemaphoreSlim _lock = new(1, 1);
    private IConnection? _connection;

    private async Task<IConnection> ConexionAsync()
    {
        if (_connection is { IsOpen: true }) return _connection;
        await _lock.WaitAsync();
        try
        {
            if (_connection is { IsOpen: true }) return _connection;
            await CerrarAsync();
            var factory = new ConnectionFactory { Uri = new Uri(amqpUrl) };
            _connection = await factory.CreateConnectionAsync("ingesta");
            return _connection;
        }
        finally { _lock.Release(); }
    }

    private async Task CerrarAsync()
    {
        if (_connection is null) return;
        try { await _connection.DisposeAsync(); } catch { /* ya estaba caída */ }
        _connection = null;
    }

    private async Task EnviarAsync(byte[] cuerpo)
    {
        var conexion = await ConexionAsync();
        await using var canal = await conexion.CreateChannelAsync(
            new CreateChannelOptions(publisherConfirmationsEnabled: true, publisherConfirmationTrackingEnabled: true));
        await canal.QueueDeclareAsync(queue: queueName, durable: true, exclusive: false, autoDelete: false);
        await canal.BasicPublishAsync(
            exchange: "",
            routingKey: queueName,
            mandatory: true,
            basicProperties: new BasicProperties { Persistent = true, ContentType = "application/json" },
            body: cuerpo);
    }

    public async Task PublicarAsync(byte[] cuerpo)
    {
        try
        {
            await EnviarAsync(cuerpo);
        }
        catch
        {
            // Un solo reintento con una conexión nueva
            await _lock.WaitAsync();
            try { await CerrarAsync(); } finally { _lock.Release(); }
            await EnviarAsync(cuerpo);
        }
    }
}