

document.addEventListener("DOMContentLoaded", _ => {

const text = document.getElementById("text-window");
const button = document.getElementById("generate-button");

button.addEventListener("click", async _ => {
    console.log("Button clicked!")
    try {
        const response = await fetch('http://localhost:1366/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                'context': text.value
            }),
        });

        const json = await response.json();
        text.value = json['context'];
        
    } catch(err) {
        console.error(`Error: ${err}`);
    }
});

});