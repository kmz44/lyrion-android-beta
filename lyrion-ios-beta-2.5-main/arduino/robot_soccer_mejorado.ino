/*
 * ROBOT SOCCER RC - VERSIÓN MEJORADA CON DIAGNÓSTICOS
 * ====================================================
 * Placa: Ingeniero Maker con Arduino Nano
 * Driver: TB6612FNG (1.2A nominal, 3A pico)
 * Incluye modo de prueba automático
 * 
 * CONFIGURACIÓN CORRECTA SEGÚN DATASHEET:
 * Motor A: PWMA=D5, AIN1=D4, AIN2=D9
 * Motor B: PWMB=D6, BIN1=D7, BIN2=D8
 */

// --- DEFINICIÓN DE PINES TB6612FNG ---
// Motor A (Izquierdo)
#define PWMA 5   // D5 - PWM velocidad motor A
#define AIN1 4   // D4 - Control dirección motor A
#define AIN2 9   // D9 - Control dirección motor A

// Motor B (Derecho)
#define PWMB 6   // D6 - PWM velocidad motor B
#define BIN1 7   // D7 - Control dirección motor B
#define BIN2 8   // D8 - Control dirección motor B

// NOTA: El TB6612FNG no necesita pines ENABLE separados
// El control de velocidad se hace directamente con PWM

// --- VARIABLES GLOBALES ---
char comando;
int velocidadAlta = 255;   // Máxima velocidad PWM (0-255)
int velocidadMedia = 200;  // Velocidad media
int velocidadBaja = 150;   // Velocidad baja
int velocidadMinima = 100; // Velocidad mínima para vencer inercia

bool modoDebug = true;     // Activar mensajes de diagnóstico
unsigned long ultimoComando = 0;
const int TIMEOUT_STOP = 2000; // Detener tras 2 seg sin comandos

void setup() {
  // Configurar pines de control como salida
  pinMode(AIN1, OUTPUT);
  pinMode(AIN2, OUTPUT);
  pinMode(PWMA, OUTPUT);
  pinMode(BIN1, OUTPUT);
  pinMode(BIN2, OUTPUT);
  pinMode(PWMB, OUTPUT);

  // Si usas pines ENABLE en L298N, descomentar:
  // pinMode(ENA, OUTPUT);
  // pinMode(ENB, OUTPUT);
  // digitalWrite(ENA, HIGH); // Activar motor A
  // digitalWrite(ENB, HIGH); // Activar motor B

  // Inicializar motores detenidos
  detenerMotores();

  // Iniciar comunicación serial
  Serial.begin(9600);
  Serial.println("====================================");
  Serial.println("🤖 ROBOT SOCCER - VERSION MEJORADA");
  Serial.println("====================================");
  Serial.println();
  
  // Mostrar configuración
  imprimirConfiguracion();
  
  // Modo de prueba automático al inicio
  Serial.println("Iniciando TEST DE MOTORES en 2 seg...");
  Serial.println("(Envía 'X' para cancelar)");
  delay(2000);
  
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'X' || c == 'x') {
      Serial.println("Test cancelado por usuario");
    } else {
      testMotores();
    }
  } else {
    testMotores();
  }
  
  Serial.println();
  Serial.println("Sistema listo. Esperando comandos...");
  imprimirComandos();
}

void loop() {
  // Leer comando si está disponible
  if (Serial.available()) {
    comando = Serial.read();
    ultimoComando = millis();
    
    // Ignorar caracteres de control
    if (comando == '\n' || comando == '\r') return;
    
    procesarComando(comando);
  }
  
  // Auto-stop por seguridad si no hay comandos
  if (millis() - ultimoComando > TIMEOUT_STOP && ultimoComando != 0) {
    detenerMotores();
    ultimoComando = 0;
  }
}

// --- PROCESAMIENTO DE COMANDOS ---
void procesarComando(char cmd) {
  Serial.print("Comando recibido: ");
  Serial.println(cmd);
  
  switch (cmd) {
    // Movimiento adelante
    case 'F': adelante(velocidadAlta); break;
    case 'f': adelante(velocidadBaja); break;
    
    // Movimiento atrás
    case 'B': atras(velocidadAlta); break;
    case 'b': atras(velocidadBaja); break;
    
    // Giro izquierda
    case 'L': izquierda(velocidadAlta); break;
    case 'l': izquierda(velocidadBaja); break;
    
    // Giro derecha
    case 'R': derecha(velocidadAlta); break;
    case 'r': derecha(velocidadBaja); break;
    
    // Detener
    case 'S': 
    case 's': detenerMotores(); break;
    
    // Modo TEST
    case 'T': 
    case 't': testMotores(); break;
    
    // Invertir Motor A (si va al revés)
    case '1': testMotorA(); break;
    
    // Invertir Motor B (si va al revés)
    case '2': testMotorB(); break;
    
    // Toggle debug
    case 'D': 
    case 'd': 
      modoDebug = !modoDebug;
      Serial.print("Debug: ");
      Serial.println(modoDebug ? "ON" : "OFF");
      break;
    
    // Ayuda
    case '?':
    case 'H':
    case 'h':
      imprimirComandos();
      break;
      
    default:
      Serial.println("Comando no reconocido. Envía '?' para ayuda");
  }
}

// --- FUNCIONES DE MOVIMIENTO MEJORADAS --- 

void adelante(int vel) {
  if (modoDebug) {
    Serial.print("⬆️  ADELANTE - Velocidad: ");
    Serial.println(vel);
  }
  
  // Motor A - Izquierdo adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, vel);
  
  // Motor B - Derecho adelante
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, vel);
}

void atras(int vel) {
  if (modoDebug) {
    Serial.print("⬇️  ATRÁS - Velocidad: ");
    Serial.println(vel);
  }
  
  // Motor A - Izquierdo atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, vel);
  
  // Motor B - Derecho atrás
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, vel);
}

void izquierda(int vel) {
  if (modoDebug) {
    Serial.print("⬅️  IZQUIERDA - Velocidad: ");
    Serial.println(vel);
  }
  
  // Motor A - Izquierdo atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, vel);
  
  // Motor B - Derecho adelante
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, vel);
}

void derecha(int vel) {
  if (modoDebug) {
    Serial.print("➡️  DERECHA - Velocidad: ");
    Serial.println(vel);
  }
  
  // Motor A - Izquierdo adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, vel);
  
  // Motor B - Derecho atrás
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, vel);
}

void detenerMotores() {
  if (modoDebug) {
    Serial.println("🛑 STOP - Motores detenidos");
  }
  
  // Frenar activamente primero (opcional - más suave)
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  
  // PWM a 0
  analogWrite(PWMA, 0);
  analogWrite(PWMB, 0);
}

// --- FUNCIONES DE DIAGNÓSTICO ---

void imprimirConfiguracion() {
  Serial.println("📋 CONFIGURACIÓN DE PINES:");
  Serial.print("   Motor A: IN1="); Serial.print(AIN1);
  Serial.print(", IN2="); Serial.print(AIN2);
  Serial.print(", PWM="); Serial.println(PWMA);
  
  Serial.print("   Motor B: IN1="); Serial.print(BIN1);
  Serial.print(", IN2="); Serial.print(BIN2);
  Serial.print(", PWM="); Serial.println(PWMB);
  Serial.println();
  
  Serial.println("⚙️  VELOCIDADES:");
  Serial.print("   Alta: "); Serial.println(velocidadAlta);
  Serial.print("   Media: "); Serial.println(velocidadMedia);
  Serial.print("   Baja: "); Serial.println(velocidadBaja);
  Serial.println();
}

void imprimirComandos() {
  Serial.println("📝 COMANDOS DISPONIBLES:");
  Serial.println("   F/f = Adelante (Alta/Baja)");
  Serial.println("   B/b = Atrás (Alta/Baja)");
  Serial.println("   L/l = Izquierda (Alta/Baja)");
  Serial.println("   R/r = Derecha (Alta/Baja)");
  Serial.println("   S/s = Stop");
  Serial.println("   T/t = Test de motores");
  Serial.println("   1   = Test solo Motor A");
  Serial.println("   2   = Test solo Motor B");
  Serial.println("   D/d = Toggle Debug");
  Serial.println("   ?/H = Ayuda");
  Serial.println();
}

// --- TEST AUTOMÁTICO DE MOTORES ---

void testMotores() {
  Serial.println();
  Serial.println("🔧 ====== TEST DE MOTORES ======");
  Serial.println();
  
  // Test Motor A
  Serial.println("🔹 Test Motor A (Izquierdo)...");
  Serial.println("   → Adelante");
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, velocidadMedia);
  delay(1500);
  
  Serial.println("   → Atrás");
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, velocidadMedia);
  delay(1500);
  
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  delay(500);
  
  // Test Motor B
  Serial.println("🔹 Test Motor B (Derecho)...");
  Serial.println("   → Adelante");
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, velocidadMedia);
  delay(1500);
  
  Serial.println("   → Atrás");
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, velocidadMedia);
  delay(1500);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
  delay(500);
  
  // Test ambos motores
  Serial.println("🔹 Test AMBOS motores...");
  Serial.println("   → Adelante");
  adelante(velocidadMedia);
  delay(1500);
  
  Serial.println("   → Atrás");
  atras(velocidadMedia);
  delay(1500);
  
  Serial.println("   → Izquierda");
  izquierda(velocidadMedia);
  delay(1000);
  
  Serial.println("   → Derecha");
  derecha(velocidadMedia);
  delay(1000);
  
  detenerMotores();
  
  Serial.println();
  Serial.println("✅ Test completado!");
  Serial.println();
  Serial.println("💡 DIAGNÓSTICO:");
  Serial.println("   - Si NO se movió: Revisar conexiones y alimentación");
  Serial.println("   - Si va al revés: Invertir cables del motor");
  Serial.println("   - Si solo 1 motor: Revisar conexiones de ese motor");
  Serial.println("   - Si gira en lugar de avanzar: Motores invertidos");
  Serial.println();
}

void testMotorA() {
  Serial.println("🔧 Test Motor A...");
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, velocidadMedia);
  delay(2000);
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  delay(2000);
  detenerMotores();
  Serial.println("✅ Test Motor A completado");
}

void testMotorB() {
  Serial.println("🔧 Test Motor B...");
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, velocidadMedia);
  delay(2000);
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  delay(2000);
  detenerMotores();
  Serial.println("✅ Test Motor B completado");
}
