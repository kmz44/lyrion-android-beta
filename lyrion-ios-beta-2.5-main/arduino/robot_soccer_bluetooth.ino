/*
 * =============================================
 * ROBOT SOCCER RC - CONTROL BLUETOOTH COMPETICION
 * =============================================
 * Placa: Ingeniero Maker con Arduino Nano
 * Driver: TB6612FNG (1.2A nominal, 3A pico)
 * Bluetooth: HC-06
 * 
 * PROTOCOLO DE COMANDOS:
 * 
 * MODO SIMPLE (Botones):
 * A/a = Avanzar (rápido/lento)
 * R/r = Retroceder (rápido/lento)
 * I/i = Izquierda (rápido/lento)
 * D/d = Derecha (rápido/lento)
 * P/p = Parar
 * T/t = Turbo ON/OFF
 * X/x = Test de motores
 * 
 * MODO JOYSTICK (Control proporcional):
 * Formato: "Jxxx,yyy" donde xxx = X (-100 a 100), yyy = Y (-100 a 100)
 * Ejemplo: "J050,100" = X=50, Y=100 (avanzar girando derecha)
 * 
 * MODO VELOCIDAD (Control PWM directo):
 * Formato: "Vxxx" donde xxx = velocidad (0-255)
 * Ejemplo: "V200" = velocidad 200
 * 
 * MODO TURBO:
 * T = Toggle turbo ON/OFF
 * 
 * PINES TB6612FNG:
 * Motor A: PWMA=D5, AIN1=D4, AIN2=D9
 * Motor B: PWMB=D6, BIN1=D7, BIN2=D8
 * =============================================
 */

// --- PINES TB6612FNG ---
#define PWMA 5   // D5 - PWM velocidad Motor A (Izquierdo)
#define AIN1 4   // D4 - Dirección Motor A
#define AIN2 9   // D9 - Dirección Motor A

#define PWMB 6   // D6 - PWM velocidad Motor B (Derecho)
#define BIN1 7   // D7 - Dirección Motor B
#define BIN2 8   // D8 - Dirección Motor B

// --- VELOCIDADES ---
int velocidadMaxima = 255;     // Velocidad máxima (100%)
int velocidadAlta = 220;       // Velocidad alta (86%)
int velocidadMedia = 180;      // Velocidad media (70%)
int velocidadBaja = 120;       // Velocidad baja (47%)
int velocidadActual = 180;     // Velocidad actual configurable

// --- VARIABLES GLOBALES ---
char comando;
String bufferComando = "";     // Buffer para comandos complejos (joystick, velocidad)
unsigned long ultimoComando = 0;
const int TIMEOUT_STOP = 1500; // Detener tras 1.5 seg sin comandos (seguridad)

// --- MODO TURBO ---
bool modoTurbo = false;
int multiplicadorTurbo = 255;  // Velocidad en modo turbo

// --- ACELERACION SUAVE ---
int velocidadMotorA = 0;       // Velocidad actual motor A
int velocidadMotorB = 0;       // Velocidad actual motor B
int velocidadObjetivoA = 0;    // Velocidad objetivo motor A
int velocidadObjetivoB = 0;    // Velocidad objetivo motor B
const int ACELERACION = 15;    // Incremento por ciclo (ajustable)

// --- JOYSTICK ---
int joystickX = 0;             // Eje X del joystick (-100 a 100)
int joystickY = 0;             // Eje Y del joystick (-100 a 100)

void setup() {
  // Configurar pines como salida
  pinMode(AIN1, OUTPUT);
  pinMode(AIN2, OUTPUT);
  pinMode(PWMA, OUTPUT);
  pinMode(BIN1, OUTPUT);
  pinMode(BIN2, OUTPUT);
  pinMode(PWMB, OUTPUT);
  
  // Inicializar motores detenidos
  detenerMotores();
  
  // Iniciar Serial para Bluetooth (9600 por defecto HC-05/HC-06)
  Serial.begin(9600);
  
  delay(100);
  // SIN MENSAJES - Solo espera comandos
}

void loop() {
  // Leer comando Bluetooth
  if (Serial.available() > 0) {
    comando = Serial.read();
    
    // Construir comando complejo (Joystick o Velocidad)
    if (comando == 'J' || comando == 'V') {
      bufferComando = comando;
      while (Serial.available() > 0) {
        char c = Serial.read();
        if (c == '\n' || c == '\r') break;
        bufferComando += c;
      }
      procesarComandoComplejo(bufferComando);
      bufferComando = "";
    }
    // Comandos simples (botones)
    else if (comando != '\n' && comando != '\r') {
      procesarComando(comando);
    }
    
    ultimoComando = millis();
  }
  
  // Aplicar aceleración suave
  aplicarAceleracionSuave();
  
  // Auto-stop de seguridad si no hay comandos
  if (millis() - ultimoComando > TIMEOUT_STOP && ultimoComando != 0) {
    detenerMotoresSuave();
    ultimoComando = 0;
  }
}

// =============================================
// PROCESAMIENTO DE COMANDOS
// =============================================

void procesarComando(char cmd) {
  int vel = modoTurbo ? multiplicadorTurbo : velocidadActual;
  
  switch (cmd) {
    // AVANZAR
    case 'A':
      avanzar(velocidadAlta);
      break;
      
    case 'a':
      avanzar(velocidadBaja);
      break;
    
    // RETROCEDER
    case 'R':
      retroceder(velocidadAlta);
      break;
      
    case 'r':
      retroceder(velocidadBaja);
      break;
    
    // IZQUIERDA
    case 'I':
      girarIzquierda(velocidadAlta);
      break;
      
    case 'i':
      girarIzquierda(velocidadBaja);
      break;
    
    // DERECHA
    case 'D':
      girarDerecha(velocidadAlta);
      break;
      
    case 'd':
      girarDerecha(velocidadBaja);
      break;
    
    // PARAR
    case 'P':
    case 'p':
      detenerMotoresSuave();
      break;
    
    // TURBO TOGGLE
    case 'T':
    case 't':
      modoTurbo = !modoTurbo;
      break;
    
    // TEST MOTORES
    case 'X':
    case 'x':
      testMotores();
      break;
    
    default:
      // Ignora comandos no reconocidos
      break;
  }
}

// =============================================
// PROCESAMIENTO DE COMANDOS COMPLEJOS
// =============================================

void procesarComandoComplejo(String cmd) {
  // Comando JOYSTICK: "Jxxx,yyy"
  if (cmd.startsWith("J")) {
    int comaPos = cmd.indexOf(',');
    if (comaPos > 0) {
      joystickX = cmd.substring(1, comaPos).toInt();
      joystickY = cmd.substring(comaPos + 1).toInt();
      
      // Limitar rango -100 a 100
      joystickX = constrain(joystickX, -100, 100);
      joystickY = constrain(joystickY, -100, 100);
      
      aplicarJoystick(joystickX, joystickY);
    }
  }
  
  // Comando VELOCIDAD: "Vxxx"
  else if (cmd.startsWith("V")) {
    int vel = cmd.substring(1).toInt();
    velocidadActual = constrain(vel, 0, 255);
  }
}

// =============================================
// FUNCIONES DE MOVIMIENTO
// =============================================

void avanzar(int vel) {
  // Motor A (Izquierdo) adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, vel);
  
  // Motor B (Derecho) adelante
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, vel);
}

void retroceder(int vel) {
  // Motor A (Izquierdo) atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, vel);
  
  // Motor B (Derecho) atrás
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, vel);
}

void girarIzquierda(int vel) {
  // Motor A (Izquierdo) atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, vel);
  
  // Motor B (Derecho) adelante
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, vel);
}

void girarDerecha(int vel) {
  // Motor A (Izquierdo) adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, vel);
  
  // Motor B (Derecho) atrás
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, vel);
}

void detenerMotores() {
  // Detener ambos motores INMEDIATO
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
  
  velocidadMotorA = 0;
  velocidadMotorB = 0;
  velocidadObjetivoA = 0;
  velocidadObjetivoB = 0;
}

void detenerMotoresSuave() {
  // Detener con desaceleración gradual
  velocidadObjetivoA = 0;
  velocidadObjetivoB = 0;
}

// =============================================
// CONTROL JOYSTICK
// =============================================

void aplicarJoystick(int x, int y) {
  // Algoritmo de mezcla para control tipo tanque
  // X = giro (-100 izq, +100 der)
  // Y = avance (-100 atrás, +100 adelante)
  
  int motorIzq = y + x;  // Motor A (Izquierdo)
  int motorDer = y - x;  // Motor B (Derecho)
  
  // Limitar a rango -100 a 100
  motorIzq = constrain(motorIzq, -100, 100);
  motorDer = constrain(motorDer, -100, 100);
  
  // Convertir a PWM (0-255)
  int velBase = modoTurbo ? multiplicadorTurbo : velocidadActual;
  int pwmIzq = map(abs(motorIzq), 0, 100, 0, velBase);
  int pwmDer = map(abs(motorDer), 0, 100, 0, velBase);
  
  // Aplicar dirección Motor A (Izquierdo)
  if (motorIzq > 5) {  // Umbral deadzone
    digitalWrite(AIN1, HIGH);
    digitalWrite(AIN2, LOW);
    velocidadObjetivoA = pwmIzq;
  } else if (motorIzq < -5) {
    digitalWrite(AIN1, LOW);
    digitalWrite(AIN2, HIGH);
    velocidadObjetivoA = pwmIzq;
  } else {
    velocidadObjetivoA = 0;
  }
  
  // Aplicar dirección Motor B (Derecho)
  if (motorDer > 5) {  // Umbral deadzone
    digitalWrite(BIN1, HIGH);
    digitalWrite(BIN2, LOW);
    velocidadObjetivoB = pwmDer;
  } else if (motorDer < -5) {
    digitalWrite(BIN1, LOW);
    digitalWrite(BIN2, HIGH);
    velocidadObjetivoB = pwmDer;
  } else {
    velocidadObjetivoB = 0;
  }
}

// =============================================
// ACELERACION SUAVE
// =============================================

void aplicarAceleracionSuave() {
  // Motor A
  if (velocidadMotorA < velocidadObjetivoA) {
    velocidadMotorA += ACELERACION;
    if (velocidadMotorA > velocidadObjetivoA) velocidadMotorA = velocidadObjetivoA;
  } else if (velocidadMotorA > velocidadObjetivoA) {
    velocidadMotorA -= ACELERACION;
    if (velocidadMotorA < velocidadObjetivoA) velocidadMotorA = velocidadObjetivoA;
  }
  
  // Motor B
  if (velocidadMotorB < velocidadObjetivoB) {
    velocidadMotorB += ACELERACION;
    if (velocidadMotorB > velocidadObjetivoB) velocidadMotorB = velocidadObjetivoB;
  } else if (velocidadMotorB > velocidadObjetivoB) {
    velocidadMotorB -= ACELERACION;
    if (velocidadMotorB < velocidadObjetivoB) velocidadMotorB = velocidadObjetivoB;
  }
  
  // Aplicar PWM
  analogWrite(PWMA, velocidadMotorA);
  analogWrite(PWMB, velocidadMotorB);
  
  // Si ambos motores están en 0, asegurar pines en LOW
  if (velocidadMotorA == 0) {
    digitalWrite(AIN1, LOW);
    digitalWrite(AIN2, LOW);
  }
  if (velocidadMotorB == 0) {
    digitalWrite(BIN1, LOW);
    digitalWrite(BIN2, LOW);
  }
}

// =============================================
// TEST DE MOTORES
// =============================================

void testMotores() {
  // Test Motor A (Izquierdo)
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, velocidadMedia);
  delay(1000);
  
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, velocidadMedia);
  delay(1000);
  
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  delay(300);
  
  // Test Motor B (Derecho)
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, velocidadMedia);
  delay(1000);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, velocidadMedia);
  delay(1000);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
  delay(300);
  
  // Test ambos motores
  avanzar(velocidadMedia);
  delay(1000);
  
  retroceder(velocidadMedia);
  delay(1000);
  
  girarIzquierda(velocidadMedia);
  delay(800);
  
  girarDerecha(velocidadMedia);
  delay(800);
  
  detenerMotores();
}


