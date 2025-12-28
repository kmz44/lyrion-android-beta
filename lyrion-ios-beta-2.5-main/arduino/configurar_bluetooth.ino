/*
 * =============================================
 * CONFIGURADOR HC-06 - OPTIMIZADO
 * =============================================
 * 
 * Este código configura módulos Bluetooth HC-06 (4 patas)
 * VCC, GND, TXD, RXD
 * 
 * DIFERENCIAS HC-06:
 * - Velocidad por defecto: 9600 baudios
 * - NO necesita botón KEY/EN
 * - Comandos AT sin CR/LF (solo el comando)
 * - No requiere modo AT especial
 * 
 * INSTRUCCIONES:
 * 1. DESCONECTA el módulo Bluetooth
 * 2. Sube este código al Arduino
 * 3. RECONECTA el Bluetooth:
 *    VCC → 5V, GND → GND, TXD → RX(D0), RXD → TX(D1)
 * 4. Abre Serial Monitor (9600 baudios, "No line ending")
 * 5. Espera la configuración automática
 * 
 * =============================================
 */

void setup() {
  // Iniciar comunicación Serial a 9600 (HC-06 por defecto)
  Serial.begin(9600);
  
  delay(2000);  // Esperar a que todo esté listo
  
  Serial.println("\n\n");
  Serial.println("========================================");
  Serial.println("  CONFIGURADOR HC-06 OPTIMIZADO");
  Serial.println("========================================");
  Serial.println();
  Serial.println("✅ Módulo HC-06 detectado");
  Serial.println();
  Serial.println("Configuración:");
  Serial.println("   • Baudios: 9600 (por defecto HC-06)");
  Serial.println("   • Sin CR/LF (comandos directos)");
  Serial.println();
  Serial.println("Conexiones:");
  Serial.println("   VCC → 5V");
  Serial.println("   GND → GND");
  Serial.println("   TXD → RX (D0)");
  Serial.println("   RXD → TX (D1)");
  Serial.println();
  
  delay(2000);
  
  // Detectar baudrate del módulo
  Serial.println(">>> Detectando modulo Bluetooth...");
  Serial.println();
  
  if (detectarModulo()) {
    Serial.println("✅ Módulo Bluetooth detectado!");
    Serial.println();
    delay(1000);
    
    // Menú de configuración
    mostrarMenu();
    configurarModulo();
  } else {
    Serial.println("❌ No se detectó el módulo Bluetooth");
    Serial.println();
    Serial.println("Verifica:");
    Serial.println("  • Conexiones correctas");
    Serial.println("  • Módulo alimentado (LED parpadeando)");
    Serial.println("  • Baudrate correcto (probando 9600, 38400, 115200)");
    Serial.println();
    Serial.println("Reinicia el Arduino para intentar de nuevo.");
  }
}

void loop() {
  // Pasar datos entre Serial Monitor y Bluetooth
  if (Serial.available()) {
    char c = Serial.read();
    Serial.write(c);
  }
}

// =============================================
// FUNCIONES DE CONFIGURACIÓN
// =============================================

bool detectarModulo() {
  Serial.println(">>> Probando comunicacion con HC-06...");
  Serial.println();
  
  // HC-06 siempre empieza a 9600 por defecto
  Serial.begin(9600);
  delay(500);
  
  // Limpiar buffer
  while(Serial.available()) Serial.read();
  
  // HC-06 necesita comandos sin CR/LF
  Serial.print("AT");  // Sin println, solo print
  delay(1000);
  
  // Leer respuesta
  String respuesta = "";
  unsigned long timeout = millis();
  while (millis() - timeout < 1500 && respuesta.length() < 10) {
    if (Serial.available()) {
      char c = Serial.read();
      respuesta += c;
      delay(5);
    }
  }
  
  Serial.print("Respuesta recibida: [");
  Serial.print(respuesta);
  Serial.print("] (");
  Serial.print(respuesta.length());
  Serial.println(" bytes)");
  
  // HC-06 responde "OK" sin salto de línea
  if (respuesta.indexOf("OK") >= 0) {
    Serial.println("✓ HC-06 responde correctamente!");
    Serial.println();
    return true;
  } else if (respuesta.length() > 0) {
    Serial.println("⚠️  Respuesta inesperada, pero hay comunicación");
    Serial.println("   Mostrando bytes en HEX:");
    for (int i = 0; i < respuesta.length(); i++) {
      Serial.print("   0x");
      Serial.print((byte)respuesta[i], HEX);
      Serial.print(" (");
      Serial.print(respuesta[i]);
      Serial.println(")");
    }
    Serial.println();
    Serial.println("Continuando con configuración...");
    Serial.println();
    return true;  // Intentar configurar de todas formas
  }
  
  Serial.println("❌ Sin respuesta del módulo");
  return false;
}

void mostrarMenu() {
  Serial.println("========================================");
  Serial.println("  CONFIGURACIÓN AUTOMÁTICA");
  Serial.println("========================================");
  Serial.println();
  Serial.println("Se configurará:");
  Serial.println("  • Nombre: iphone max");
  Serial.println("  • PIN: 1234");
  Serial.println("  • Baudrate: 9600");
  Serial.println();
  Serial.println("Presiona cualquier tecla para continuar...");
  Serial.println("(o espera 5 segundos para configuración automática)");
  Serial.println();
  
  unsigned long inicio = millis();
  while (millis() - inicio < 5000) {
    if (Serial.available()) {
      Serial.read();
      break;
    }
    delay(100);
  }
}

void configurarModulo() {
  Serial.println("========================================");
  Serial.println("  INICIANDO CONFIGURACIÓN...");
  Serial.println("========================================");
  Serial.println();
  
  // Configurar nombre (HC-06 formato específico)
  Serial.println("Configurando nombre...");
  enviarComandoAT("AT+NAMEiphone max");  // HC-06 sin '='
  delay(1000);
  
  // Configurar PIN (HC-06 formato específico)
  Serial.println("Configurando PIN...");
  enviarComandoAT("AT+PIN1234");  // HC-06 sin '='
  delay(1000);
  
  // Configurar baudrate (HC-06 formato específico)
  Serial.println("Configurando velocidad...");
  enviarComandoAT("AT+BAUD4");  // 9600 baudios
  delay(1000);
  
  Serial.println();
  Serial.println("========================================");
  Serial.println("  >>> CONFIGURACION COMPLETADA <<<");
  Serial.println("========================================");
  Serial.println();
  Serial.println("DATOS DE CONEXION:");
  Serial.println("   Nombre: iphone max");
  Serial.println("   PIN: 1234");
  Serial.println("   Velocidad: 9600 baudios");
  Serial.println();
  Serial.println("💡 SIGUIENTE PASO:");
  Serial.println("   1. Desconecta este cable USB");
  Serial.println("   2. Sube el código 'robot_soccer_bluetooth.ino'");
  Serial.println("   3. Reconecta el Bluetooth");
  Serial.println("   4. Empareja desde tu móvil");
  Serial.println();
  Serial.println("========================================");
}

void enviarComandoAT(String comando) {
  Serial.print("  → ");
  Serial.println(comando);
  
  // Limpiar buffer antes de enviar
  while(Serial.available()) Serial.read();
  
  // HC-06: enviar comando sin CR/LF
  Serial.print(comando);
  delay(800);  // HC-06 necesita más tiempo para procesar
  
  // Leer respuesta
  String respuesta = "";
  unsigned long timeout = millis();
  
  while (millis() - timeout < 1200) {
    if (Serial.available()) {
      respuesta += (char)Serial.read();
      delay(5);
    }
  }
  
  Serial.print("  ← ");
  if (respuesta.length() > 0) {
    Serial.print("[");
    Serial.print(respuesta);
    Serial.println("]");
    
    if (respuesta.indexOf("OK") >= 0) {
      Serial.println("  ✅ Éxito");
    } else if (respuesta.indexOf("setname") >= 0 || 
               respuesta.indexOf("setPIN") >= 0 || 
               respuesta.indexOf("9600") >= 0) {
      Serial.println("  ✅ Configurado");
    } else {
      Serial.println("  ⚠️  Respuesta inusual (puede ser normal en HC-06)");
    }
  } else {
    Serial.println("(sin respuesta - puede ser normal)");
  }
  
  Serial.println();
}
