import Foundation

struct QISDegreeProgram: Sendable, Identifiable, Hashable {
    let id: String
    let title: String
    let url: URL
    let degreeCode: String
    let majorCode: String
}
