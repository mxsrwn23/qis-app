import Foundation

struct User: Codable {
    let id: String
    let name: String
    let email: String
    let degreeProgram: QISDegreeProgram?
}
