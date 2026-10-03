import Foundation

class API {
    static let shared = API()
    private let baseURL = URL(string: "https://your-api-url.com")!
    
    func fetchDegreePrograms(completion: @escaping ([QISDegreeProgram]?, Error?) -> Void) {
        let url = baseURL.appendingPathComponent("degreePrograms")
        let task = URLSession.shared.dataTask(with: url) { data, response, error in
            if let error = error {
                completion(nil, error)
                return
            }
            guard let data = data else {
                completion(nil, NSError(domain: "No data", code: 0, userInfo: nil))
                return
            }
            do {
                let decoder = JSONDecoder()
                let degreePrograms = try decoder.decode([QISDegreeProgram].self, from: data)
                completion(degreePrograms, nil)
            } catch {
                completion(nil, error)
            }
        }
        task.resume()
    }
    
    func updateUserDegreeProgram(userId: String, degreeProgramId: String, completion: @escaping (User?, Error?) -> Void) {
        let url = baseURL.appendingPathComponent("users/\(userId)")
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let user = User(id: userId, name: "", email: "", degreeProgram: nil)
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        do {
            let jsonData = try encoder.encode(user)
            request.httpBody = jsonData
        } catch {
            completion(nil, error)
            return
        }
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(nil, error)
                return
            }
            guard let data = data else {
                completion(nil, NSError(domain: "No data", code: 0, userInfo: nil))
                return
            }
            do {
                let decoder = JSONDecoder()
                let updatedUser = try decoder.decode(User.self, from: data)
                completion(updatedUser, nil)
            } catch {
                completion(nil, error)
            }
        }
        task.resume()
    }
}
