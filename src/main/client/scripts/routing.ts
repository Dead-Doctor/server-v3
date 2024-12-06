export const getData = <T>(id: string): T => {
    let scriptElement = document.querySelector(`script[type="application/json"]#data-${id}`)!
    return JSON.parse(scriptElement.textContent!)
}