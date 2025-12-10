Behaviour.specify("#svnerrorlink", "SubversionSCMSource_showDetails", 0, (element) => {
    element.addEventListener("click", (event) => {
        event.preventDefault();

        document.getElementById("svnerror").style.display = "block";
        event.target.style.display = "none";
    });
});
