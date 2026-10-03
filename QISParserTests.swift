import XCTest
@testable import YourProjectName

class QISParserTests: XCTestCase {
    
    func testParsing() throws {
        let htmlData = """
        <ul class="treelist">
            <li class="treelist">
                <a class="regular" href="?asi=123&stgnr=11&abschl=04">Abschluss Bachelor of Science</a>
            </li>
            <li class="treelist">
                <a class="regular" href="?asi=456&stgnr=22&abschl=05">Master of Science</a>
            </li>
        </ul>
        """.data(using: .utf8)!
        
        let parser = QISParser(data: htmlData)
        let programs = try parser.parse()
        
        XCTAssertEqual(programs.count, 2)
        
        let firstProgram = programs[0]
        XCTAssertEqual(firstProgram.id, "https://qis.hochschule-trier.de/?asi=123&stgnr=11&abschl=04")
        XCTAssertEqual(firstProgram.title, "Abschluss Bachelor of Science")
        XCTAssertEqual(firstProgram.url.absoluteString, "https://qis.hochschule-trier.de/?asi=123&stgnr=11&abschl=04")
        XCTAssertEqual(firstProgram.degreeCode, "04")
        XCTAssertEqual(firstProgram.majorCode, "11")
        
        let secondProgram = programs[1]
        XCTAssertEqual(secondProgram.id, "https://qis.hochschule-trier.de/?asi=456&stgnr=22&abschl=05")
        XCTAssertEqual(secondProgram.title, "Master of Science")
        XCTAssertEqual(secondProgram.url.absoluteString, "https://qis.hochschule-trier.de/?asi=456&stgnr=22&abschl=05")
        XCTAssertEqual(secondProgram.degreeCode, "05")
        XCTAssertEqual(secondProgram.majorCode, "22")
    }
}
