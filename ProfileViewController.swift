import UIKit

class ProfileViewController: UIViewController {
    private let degreeProgramPicker = UIPickerView()
    private var degreePrograms: [QISDegreeProgram] = []
    private var selectedDegreeProgram: QISDegreeProgram?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Profil"
        
        degreeProgramPicker.delegate = self
        degreeProgramPicker.dataSource = self
        
        fetchDegreePrograms()
    }
    
    private func fetchDegreePrograms() {
        API.shared.fetchDegreePrograms { [weak self] degreePrograms, error in
            if let error = error {
                print("Error fetching degree programs: \(error)")
                return
            }
            DispatchQueue.main.async {
                self?.degreePrograms = degreePrograms ?? []
                self?.degreeProgramPicker.reloadAllComponents()
            }
        }
    }
    
    private func updateUserDegreeProgram() {
        guard let selectedDegreeProgram = selectedDegreeProgram else {
            print("No degree program selected")
            return
        }
        
        API.shared.updateUserDegreeProgram(userId: "user123", degreeProgramId: selectedDegreeProgram.id) { [weak self] user, error in
            if let error = error {
                print("Error updating user degree program: \(error)")
                return
            }
            DispatchQueue.main.async {
                self?.showAlert(title: "Success", message: "Degree program updated successfully")
            }
        }
    }
    
    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
        present(alert, animated: true, completion: nil)
    }
}

extension ProfileViewController: UIPickerViewDelegate, UIPickerViewDataSource {
    func numberOfComponents(in pickerView: UIPickerView) -> Int {
        return 1
    }
    
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        return degreePrograms.count
    }
    
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        return degreePrograms[row].title
    }
    
    func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
        selectedDegreeProgram = degreePrograms[row]
        updateUserDegreeProgram()
    }
}
