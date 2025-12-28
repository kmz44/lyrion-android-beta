
import SwiftUI

struct GroupListView: View {
    @State private var myGroups: [SupabaseClient.GroupDTO] = []
    @State private var isLoading = false
    @State private var showCreateGroup = false
    
    var body: some View {
        VStack {
            if isLoading {
                Spacer()
                ProgressView()
                    .scaleEffect(1.2)
                Spacer()
            } else if myGroups.isEmpty {
                Spacer()
                VStack(spacing: 24) {
                    Image(systemName: "person.3.fill")
                        .font(.system(size: 70))
                        .foregroundColor(.gray.opacity(0.6))
                    
                    VStack(spacing: 8) {
                        Text("No estás en ningún grupo")
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        Text("Crea uno para empezar a compartir con tus amigos o seguidores")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                    
                    Button {
                        showCreateGroup = true
                    } label: {
                        Text("Crear un Grupo")
                            .fontWeight(.bold)
                            .foregroundColor(.black)
                            .padding(.horizontal, 32)
                            .padding(.vertical, 14)
                            .background(Color.white)
                            .cornerRadius(12)
                            .shadow(color: .white.opacity(0.1), radius: 10, x: 0, y: 5)
                    }
                }
                Spacer()
                Spacer().frame(height: 100) // Extra space for TabBar
            } else {
                List {
                    ForEach(myGroups) { group in
                        NavigationLink(destination: GroupDetailView(group: group)) {
                            GroupRow(group: group)
                        }
                    }
                    .listRowBackground(Color.clear)
                }
                .listStyle(.plain)
                .refreshable {
                    await loadGroups()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black) // Consistent with the app's dark theme
        .onAppear {
            Task {
                await loadGroups()
            }
        }
        .sheet(isPresented: $showCreateGroup) {
            CreateGroupView { newGroup in
                myGroups.insert(newGroup, at: 0)
            }
        }
    }
    
    func loadGroups() async {
        isLoading = true
        defer { isLoading = false }
        do {
            myGroups = try await SupabaseClient.shared.fetchMyGroups()
        } catch {
            print("Error loading groups: \(error)")
        }
    }
}

struct GroupRow: View {
    let group: SupabaseClient.GroupDTO
    
    var body: some View {
        HStack(spacing: 12) {
            // Placeholder Image
            Circle()
                .fill(Color.gray.opacity(0.3))
                .overlay(
                    Text(group.name.prefix(1).uppercased())
                        .font(.title3)
                        .fontWeight(.bold)
                )
                .frame(width: 50, height: 50)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(group.name)
                    .font(.headline)
                    .foregroundColor(.white)
                
                Text(group.type.capitalized)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(6)
            }
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.gray)
                .font(.caption)
        }
        .padding()
        .background(Color.white.opacity(0.05))
        .cornerRadius(12)
        .padding(.vertical, 4)
    }
}
