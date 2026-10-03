import Foundation

enum QISParseError: Error {
    case invalidHTML
    case missingURL
    case missingTitle
    case invalidURL
}

class QISParser: NSObject, XMLParserDelegate {
    private var parser: XMLParser
    private var currentElement = ""
    private var currentTitle = ""
    private var currentURL = ""
    private var degreePrograms: [QISDegreeProgram] = []
    
    init(data: Data) {
        parser = XMLParser(data: data)
        parser.delegate = self
    }
    
    func parse() throws -> [QISDegreeProgram] {
        guard parser.parse() else {
            throw QISParseError.invalidHTML
        }
        return degreePrograms
    }
    
    // MARK: - XMLParserDelegate Methods
    
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        currentElement = elementName
        if elementName == "a" && attributeDict["class"] == "regular" {
            currentURL = attributeDict["href"] ?? ""
        }
    }
    
    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if currentElement == "a" {
            currentTitle += string.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
    
    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if elementName == "a" {
            guard let url = URL(string: currentURL, relativeTo: URL(string: "https://qis.hochschule-trier.de")!) else {
                currentTitle = ""
                currentURL = ""
                return
            }
            
            guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
                  let queryItems = components.queryItems else {
                currentTitle = ""
                currentURL = ""
                return
            }
            
            var degreeCode = ""
            var majorCode = ""
            
            for item in queryItems {
                if item.name == "abschl" {
                    degreeCode = item.value ?? ""
                } else if item.name == "stgnr" {
                    majorCode = item.value ?? ""
                }
            }
            
            let program = QISDegreeProgram(id: url.absoluteString, title: currentTitle, url: url, degreeCode: degreeCode, majorCode: majorCode)
            degreePrograms.append(program)
            
            currentTitle = ""
            currentURL = ""
        }
    }
}
