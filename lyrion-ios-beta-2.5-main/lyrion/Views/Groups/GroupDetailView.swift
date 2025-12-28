
import SwiftUI

struct GroupDetailView: View {
    let group: SupabaseClient.GroupDTO
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header
                HStack(alignment: .top) {
                    Circle()
                        .fill(Color.gray.opacity(0.3))
                        .overlay(
                            Text(group.name.prefix(1).uppercased())
                                .font(.largeTitle)
                                .fontWeight(.bold)
                        )
                        .frame(width: 80, height: 80)
                    
                    VStack(alignment: .leading) {
                        Text(group.name)
                            .font(.title)
                            .fontWeight(.bold)
                        
                        if let desc = group.description, !desc.isEmpty {
                            Text(desc)
                                .foregroundColor(.secondary)
                        }
                        
                        HStack {
                            Image(systemName: "lock.fill") // Icon based on type
                                .font(.caption)
                            Text(group.type.capitalized)
                                .font(.caption)
                        }
                        .padding(6)
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(6)
                    }
                    Spacer()
                }
                .padding()
                
                Divider()
                
                // Feed Section (Placeholder)
                VStack {
                    Text("Publicaciones del Grupo")
                        .font(.headline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                    
                    // Here we would use a modified PostListView/Feed that filters by group_id
                    Text("Próximamente: Feed de publicaciones del grupo.")
                        .foregroundColor(.gray)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(10)
                        .padding()
                }
                
                Spacer()
            }
        }
        .navigationTitle(group.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
