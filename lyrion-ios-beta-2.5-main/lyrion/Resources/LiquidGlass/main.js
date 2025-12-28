import {
  FilesetResolver,
  HandLandmarker
} from "@mediapipe/tasks-vision";

document.addEventListener('DOMContentLoaded', async () => {
  const videoElement = document.getElementById('camera-feed');
  const cursor = document.getElementById('hand-cursor');
  let handLandmarker = undefined;
  let webcamRunning = false;
  let lastVideoTime = -1;

  // --- MediaPipe Setup ---
  const createHandLandmarker = async () => {
    const vision = await FilesetResolver.forVisionTasks(
      "./assets/wasm" // Relative path for iOS bundle
    );
    handLandmarker = await HandLandmarker.createFromOptions(vision, {
      baseOptions: {
        modelAssetPath: `./assets/hand_landmarker.task`, // Relative path for iOS bundle
        delegate: "GPU"
      },
      runningMode: "VIDEO",
      numHands: 1
    });
  };

  await createHandLandmarker();

  // --- Camera Setup ---
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'user',
        width: { ideal: 1280 },
        height: { ideal: 720 }
      }
    });
    videoElement.srcObject = stream;
    videoElement.addEventListener('loadeddata', predictWebcam);
    webcamRunning = true;

    // Reduce blur slightly for visibility
    videoElement.style.filter = 'blur(5px)';

  } catch (err) {
    console.error("Error accessing camera:", err);
    alert("Camera access is required for hand tracking.");
  }

  // --- Hand Tracking Loop ---
  async function predictWebcam() {
    if (lastVideoTime !== videoElement.currentTime) {
      lastVideoTime = videoElement.currentTime;

      if (handLandmarker) {
        const startTimeMs = performance.now();
        const results = handLandmarker.detectForVideo(videoElement, startTimeMs);

        if (results.landmarks && results.landmarks.length > 0) {
          const landmarks = results.landmarks[0];

          // 1. Get Index Finger Tip (Landmark 8) for cursor position
          // Mirror X coordinate because webcam is mirrored
          const x = (1 - landmarks[8].x) * window.innerWidth;
          const y = landmarks[8].y * window.innerHeight;

          updateCursor(x, y);

          // 2. Check for Pinch (Thumb Tip 4 and Index Tip 8)
          const thumbTip = landmarks[4];
          const indexTip = landmarks[8];
          const distance = Math.hypot(
            (thumbTip.x - indexTip.x),
            (thumbTip.y - indexTip.y)
          );

          // Umbral ajustado para mejor detección (0.05 es aprox 5% de la pantalla)
          // Si está muy cerca, es un click
          if (distance < 0.06) {
            if (!isPinching) {
              handlePinch(x, y);
            }
          } else if (distance > 0.1) {
            // Histéresis: solo soltar si se separan suficiente
            releasePinch();
          }
        } else {
          cursor.style.display = 'none';
        }
      }
    }

    if (webcamRunning) {
      window.requestAnimationFrame(predictWebcam);
    }
  }

  // --- Interaction Logic ---
  function updateCursor(x, y) {
    cursor.style.display = 'block';
    cursor.style.left = `${x}px`;
    cursor.style.top = `${y}px`;

    // Hover effects
    // Usamos x, y directamente. elementFromPoint usa coordenadas de viewport.
    const element = document.elementFromPoint(x, y);
    document.querySelectorAll('.hovered').forEach(el => el.classList.remove('hovered'));

    if (element) {
      const clickable = element.closest('.glass-button, .dock-item, #character-img');
      if (clickable) {
        clickable.classList.add('hovered');
        cursor.classList.add('hover-active'); // Feedback visual en el cursor
      } else {
        cursor.classList.remove('hover-active');
      }
    }
  }

  let isPinching = false;

  function handlePinch(x, y) {
    isPinching = true;
    cursor.classList.add('pinching');

    // Trigger click
    const element = document.elementFromPoint(x, y);
    if (element) {
      // Buscar el elemento clickeable más cercano
      const clickable = element.closest('.glass-button, .dock-item, button, a');

      if (clickable) {
        console.log("Clicking:", clickable);
        clickable.click();

        // Efecto visual de click
        clickable.classList.add('clicked');
        setTimeout(() => clickable.classList.remove('clicked'), 200);
      }
    }
  }

  function releasePinch() {
    isPinching = false;
    cursor.classList.remove('pinching');
  }

  // 3D Tilt Effect (Disable on mobile)
  const glassWindow = document.querySelector('.glass-window');
  document.addEventListener('mousemove', (e) => {
    if (window.innerWidth > 600) {
      const xAxis = (window.innerWidth / 2 - e.pageX) / 25;
      const yAxis = (window.innerHeight / 2 - e.pageY) / 25;
      glassWindow.style.transform = `rotateY(${xAxis}deg) rotateX(${yAxis}deg)`;
    } else {
      glassWindow.style.transform = 'none';
    }
  });

  // --- Stats Table Logic ---
  const exploreBtn = document.querySelector('.glass-button'); // The "Explorar" button
  const glassPanel = document.querySelector('.glass-panel');

  // Categorized Stats Data
  const statsCategories = {
    "Físico": [
      { name: "Fuerza", desc: "Potencia muscular total." },
      { name: "Resistencia", desc: "Aguante físico general." },
      { name: "Velocidad", desc: "Rapidez de movimiento." },
      { name: "Agilidad", desc: "Flexibilidad y equilibrio." },
      { name: "Coordinación", desc: "Precisión de movimientos." },
      { name: "Vitalidad", desc: "Salud y robustez." },
      { name: "Destreza", desc: "Habilidad manual fina." },
      { name: "Kinestesia", desc: "Conciencia corporal." },
      { name: "Armadura natural", desc: "Resistencia física." },
      { name: "Reflejos", desc: "Tiempo de reacción." },
      { name: "Stamina", desc: "Energía de acción." }
    ],
    "Mental": [
      { name: "Inteligencia", desc: "Aprendizaje y lógica." },
      { name: "Percepción", desc: "Notar detalles." },
      { name: "Voluntad", desc: "Resistencia mental." },
      { name: "Creatividad", desc: "Imaginación." },
      { name: "Atención", desc: "Concentración." },
      { name: "Conocimiento técnico", desc: "Saber especializado." },
      { name: "Medicina", desc: "Curación y diagnóstico." },
      { name: "Visión", desc: "Claridad visual." },
      { name: "Audición", desc: "Sensibilidad auditiva." },
      { name: "Olfato / Gusto", desc: "Sentidos químicos." },
      { name: "Consciencia", desc: "Entendimiento profundo." },
      { name: "Control interno", desc: "Autocontrol." }
    ],
    "Social": [
      { name: "Carisma", desc: "Influencia personal." },
      { name: "Estabilidad emocional", desc: "Control de emociones." },
      { name: "Empatía", desc: "Entender a otros." },
      { name: "Confianza", desc: "Seguridad." },
      { name: "Reputación", desc: "Fama o estatus." },
      { name: "Influencia", desc: "Poder sobre grupos." },
      { name: "Negociación", desc: "Tratos y comercio." },
      { name: "Liderazgo", desc: "Dirigir a otros." } // Added implicitly based on description
    ],
    "Otros": [
      { name: "Supervivencia", desc: "Subsistencia natural." },
      { name: "Defensa mágica", desc: "Protección sobrenatural." },
      { name: "Mana", desc: "Energía mágica." },
      { name: "Aura", desc: "Presencia energética." },
      { name: "Suerte", desc: "Probabilidad favorable." },
      { name: "Afinidad elemental", desc: "Conexión elemental." },
      { name: "Adaptación", desc: "Ajuste al entorno." },
      { name: "Habilidad única", desc: "Don especial." },
      { name: "Moralidad", desc: "Alineación ética." },
      { name: "Ambición", desc: "Deseo de poder." },
      { name: "Lealtad", desc: "Fidelidad." }
    ]
  };

  exploreBtn.addEventListener('click', () => {
    showCategoryMenu();
  });

  function showCategoryMenu() {
    let menuHTML = `
      <div class="stats-header">
        <h2>Categorías</h2>
        <button class="glass-button small" id="back-to-main">Volver</button>
      </div>
      <div class="category-menu">
    `;

    for (const category of Object.keys(statsCategories)) {
      menuHTML += `
        <button class="glass-button category-btn" data-category="${category}">
          ${category}
        </button>
      `;
    }

    menuHTML += `</div>`;

    glassPanel.innerHTML = menuHTML;
    glassPanel.classList.add('showing-stats');

    // Bind Events
    document.getElementById('back-to-main').addEventListener('click', () => {
      resetToMain();
    });

    document.querySelectorAll('.category-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const category = e.target.dataset.category;
        showStatsForCategory(category);
      });
    });
  }

  function showStatsForCategory(category, page = 0) {
    const stats = statsCategories[category];
    const itemsPerPage = 6; // Fit 6 items comfortably
    const totalPages = Math.ceil(stats.length / itemsPerPage);

    const start = page * itemsPerPage;
    const end = start + itemsPerPage;
    const currentStats = stats.slice(start, end);

    let contentHTML = `
      <div class="stats-header">
        <h2>${category} <span style="font-size: 0.8rem; opacity: 0.7">(${page + 1}/${totalPages})</span></h2>
        <button class="glass-button small" id="back-to-menu">Atrás</button>
      </div>
      <div class="stats-grid">
    `;

    currentStats.forEach(stat => {
      contentHTML += `
        <div class="stat-card">
          <div class="stat-name">${stat.name}</div>
          <div class="stat-desc">${stat.desc}</div>
        </div>
      `;
    });

    contentHTML += `</div>`;

    // Pagination Controls
    if (totalPages > 1) {
      contentHTML += `<div class="pagination-controls">`;

      if (page > 0) {
        contentHTML += `<button class="glass-button small" id="prev-page">⬅ Anterior</button>`;
      } else {
        contentHTML += `<div></div>`; // Spacer
      }

      if (page < totalPages - 1) {
        contentHTML += `<button class="glass-button small" id="next-page">Siguiente ➡</button>`;
      }

      contentHTML += `</div>`;
    }

    glassPanel.innerHTML = contentHTML;

    // Bind Events
    document.getElementById('back-to-menu').addEventListener('click', () => {
      showCategoryMenu();
    });

    if (document.getElementById('prev-page')) {
      document.getElementById('prev-page').addEventListener('click', () => {
        showStatsForCategory(category, page - 1);
      });
    }

    if (document.getElementById('next-page')) {
      document.getElementById('next-page').addEventListener('click', () => {
        showStatsForCategory(category, page + 1);
      });
    }
  }

  function resetToMain() {
    glassPanel.innerHTML = `
      <h1>Hola, Usuario</h1>
      <p>Bienvenido a tu experiencia de computación espacial.</p>
      <button class="glass-button">Explorar</button>
    `;
    glassPanel.classList.remove('showing-stats');

    // Re-attach listener
    document.querySelector('.glass-button').addEventListener('click', () => showCategoryMenu());
  }
});
