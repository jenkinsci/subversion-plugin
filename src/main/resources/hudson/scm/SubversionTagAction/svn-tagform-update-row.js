// update the visual feedback depending on the checkbox state
function updateRow(changeEvent) {
    const checkbox = changeEvent.target;
    checkbox.parentNode.parentNode.style.color = checkbox.checked ? "inherit" : "grey";

    const index = checkbox.getAttribute("data-index");
    document.querySelector(`input[name="name${index}"]`).disabled = !checkbox.checked;
}

window.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".svn-tagform-tag-checkbox").forEach(checkbox => {
        document.addEventListener("change", updateRow);
    });
});
