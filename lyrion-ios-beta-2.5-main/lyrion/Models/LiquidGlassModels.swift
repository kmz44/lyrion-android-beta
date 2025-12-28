//
//  LiquidGlassModels.swift
//  Lyrion
//

import Foundation

struct StatCategory {
    let name: String
    let stats: [StatItem]
}

struct StatItem: Identifiable {
    let id = UUID()
    let name: String
    let description: String
}

// Stats data organized by categories
let statsCategories: [StatCategory] = [
    StatCategory(name: "Físico", stats: [
        StatItem(name: "Fuerza", description: "Potencia muscular total."),
        StatItem(name: "Resistencia", description: "Aguante físico general."),
        StatItem(name: "Velocidad", description: "Rapidez de movimiento."),
        StatItem(name: "Agilidad", description: "Flexibilidad y equilibrio."),
        StatItem(name: "Coordinación", description: "Precisión de movimientos."),
        StatItem(name: "Vitalidad", description: "Salud y robustez."),
        StatItem(name: "Destreza", description: "Habilidad manual fina."),
        StatItem(name: "Kinestesia", description: "Conciencia corporal."),
        StatItem(name: "Armadura natural", description: "Resistencia física."),
        StatItem(name: "Reflejos", description: "Tiempo de reacción."),
        StatItem(name: "Stamina", description: "Energía de acción.")
    ]),
    StatCategory(name: "Mental", stats: [
        StatItem(name: "Inteligencia", description: "Aprendizaje y lógica."),
        StatItem(name: "Percepción", description: "Notar detalles."),
        StatItem(name: "Voluntad", description: "Resistencia mental."),
        StatItem(name: "Creatividad", description: "Imaginación."),
        StatItem(name: "Atención", description: "Concentración."),
        StatItem(name: "Conocimiento técnico", description: "Saber especializado."),
        StatItem(name: "Medicina", description: "Curación y diagnóstico."),
        StatItem(name: "Visión", description: "Claridad visual."),
        StatItem(name: "Audición", description: "Sensibilidad auditiva."),
        StatItem(name: "Olfato / Gusto", description: "Sentidos químicos."),
        StatItem(name: "Consciencia", description: "Entendimiento profundo."),
        StatItem(name: "Control interno", description: "Autocontrol.")
    ]),
    StatCategory(name: "Social", stats: [
        StatItem(name: "Carisma", description: "Influencia personal."),
        StatItem(name: "Estabilidad emocional", description: "Control de emociones."),
        StatItem(name: "Empatía", description: "Entender a otros."),
        StatItem(name: "Confianza", description: "Seguridad."),
        StatItem(name: "Reputación", description: "Fama o estatus."),
        StatItem(name: "Influencia", description: "Poder sobre grupos."),
        StatItem(name: "Negociación", description: "Tratos y comercio."),
        StatItem(name: "Liderazgo", description: "Dirigir a otros.")
    ]),
    StatCategory(name: "Otros", stats: [
        StatItem(name: "Supervivencia", description: "Subsistencia natural."),
        StatItem(name: "Defensa mágica", description: "Protección sobrenatural."),
        StatItem(name: "Mana", description: "Energía mágica."),
        StatItem(name: "Aura", description: "Presencia energética."),
        StatItem(name: "Suerte", description: "Probabilidad favorable."),
        StatItem(name: "Afinidad elemental", description: "Conexión elemental."),
        StatItem(name: "Adaptación", description: "Ajuste al entorno."),
        StatItem(name: "Habilidad única", description: "Don especial."),
        StatItem(name: "Moralidad", description: "Alineación ética."),
        StatItem(name: "Ambición", description: "Deseo de poder."),
        StatItem(name: "Lealtad", description: "Fidelidad.")
    ])
]
