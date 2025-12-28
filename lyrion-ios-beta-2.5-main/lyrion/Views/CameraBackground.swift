//
//  CameraBackground.swift
//  Lyrion
//
//

import SwiftUI
import AVFoundation

struct CameraBackground: UIViewRepresentable {
    @Binding var cameraPosition: AVCaptureDevice.Position
    
    func makeUIView(context: Context) -> CameraPreviewView {
        let view = CameraPreviewView(position: cameraPosition)
        return view
    }
    
    func updateUIView(_ uiView: CameraPreviewView, context: Context) {
        if uiView.currentPosition != cameraPosition {
            uiView.switchCamera(to: cameraPosition)
        }
    }
}

class CameraPreviewView: UIView {
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private(set) var currentPosition: AVCaptureDevice.Position
    
    // Cola dedicada para operaciones de cámara para evitar bloquear el Main Thread
    private let cameraQueue = DispatchQueue(label: "com.lyrion.cameraQueue")
    
    init(position: AVCaptureDevice.Position = .back) {
        self.currentPosition = position
        super.init(frame: .zero)
        requestCameraPermission()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func requestCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera(position: currentPosition)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted {
                    self?.setupCamera(position: self?.currentPosition ?? .back)
                } else {
                    DispatchQueue.main.async {
                        self?.showGradientFallback()
                    }
                }
            }
        case .denied, .restricted:
            showGradientFallback()
        @unknown default:
            showGradientFallback()
        }
    }
    
    func switchCamera(to position: AVCaptureDevice.Position) {
        currentPosition = position
        
        // Ejecutar en background queue
        cameraQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Stop current session
            self.captureSession?.stopRunning()
            
            DispatchQueue.main.async {
                // Remove old preview layer
                self.previewLayer?.removeFromSuperlayer()
                self.previewLayer = nil
            }
            
            // Setup new camera
            self.setupCameraInternal(position: position)
        }
    }
    
    private func setupCamera(position: AVCaptureDevice.Position) {
        cameraQueue.async { [weak self] in
            self?.setupCameraInternal(position: position)
        }
    }
    
    private func setupCameraInternal(position: AVCaptureDevice.Position) {
        let session = AVCaptureSession()
        session.sessionPreset = .medium
        
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            DispatchQueue.main.async {
                self.showGradientFallback()
            }
            return
        }
        
        do {
            let input = try AVCaptureDeviceInput(device: camera)
            if session.canAddInput(input) {
                session.addInput(input)
            }
            
            // Crear layer en Main Thread pero configurarlo con la sesión
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                
                let preview = AVCaptureVideoPreviewLayer(session: session)
                preview.videoGravity = .resizeAspectFill
                preview.frame = self.bounds
                self.layer.insertSublayer(preview, at: 0)
                self.previewLayer = preview
                
                // Actualizar referencia de sesión
                self.captureSession = session
                
                // Iniciar sesión en background queue
                self.cameraQueue.async {
                    session.startRunning()
                }
            }
        } catch {
            print("Camera error: \(error)")
            DispatchQueue.main.async {
                self.showGradientFallback()
            }
        }
    }
    
    private func showGradientFallback() {
        // Asegurar que esto corre en Main Thread
        if !Foundation.Thread.isMainThread {
            DispatchQueue.main.async {
                self.showGradientFallback()
            }
            return
        }
        
        let gradient = CAGradientLayer()
        gradient.frame = self.bounds
        gradient.colors = [
            UIColor(red: 0.4, green: 0.49, blue: 0.91, alpha: 0.7).cgColor,
            UIColor(red: 0.46, green: 0.29, blue: 0.63, alpha: 0.7).cgColor,
            UIColor(red: 0.94, green: 0.58, blue: 0.98, alpha: 0.5).cgColor
        ]
        gradient.startPoint = CGPoint(x: 0, y: 0)
        gradient.endPoint = CGPoint(x: 1, y: 1)
        self.layer.insertSublayer(gradient, at: 0)
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = self.bounds
        
        if let gradient = layer.sublayers?.first(where: { $0 is CAGradientLayer }) {
            gradient.frame = bounds
        }
    }
    
    deinit {
        let session = captureSession
        cameraQueue.async {
            session?.stopRunning()
        }
    }
}
