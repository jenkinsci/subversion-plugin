function updateRow(e, i) {
    e.parentNode.parentNode.style.color = e.checked ? "inherit" : "grey";
    document.getElementById("name" + i).disabled = !e.checked;
}