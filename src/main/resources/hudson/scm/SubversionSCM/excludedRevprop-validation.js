Behaviour.specify("input[name='_.remote']", 'SubversionSCM.RemoteLocation', 0, function(element) {
    element.addEventListener('blur', updateHiddenFields);
});

Behaviour.specify("select[name='_.credentialsId'][filldependson='remote']", 'SubversionSCM.CredentialsId', 0, function(element) {
    element.addEventListener('change', updateHiddenFields);
});

function updateHiddenFields() {

    var remoteLocationElement = document.querySelector("input[name='_.remote']");
    var credentialsIdElement = document.querySelector("select[name='_.credentialsId'][filldependson='remote']");
    var selectedOption = credentialsIdElement.options[credentialsIdElement.selectedIndex].value;


    var remoteHidden = document.querySelector("input[name='_.remoteLocation']");
    var credentialsHidden = document.querySelector("input[name='_.remoteCredentialsId']");

    if (remoteHidden) {
        remoteHidden.value = remoteLocationElement.value;
    }

    if (credentialsHidden) {
        credentialsHidden.value = selectedOption;
    }

    var revPropField = document.querySelector("input[name='_.excludedRevprop']");
    if (revPropField) {
        revPropField.dispatchEvent(new Event('change'));
    }
}
