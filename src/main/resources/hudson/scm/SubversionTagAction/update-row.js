function updateRow(e, i) {
    e.parentNode.parentNode.style.color = e.checked ? "red" : "blue";
    document.getElementById("name" + i).disabled = !e.checked;
}