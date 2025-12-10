package com.guillem.llistacompra

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.UUID

// --- 1. MODELS DE DADES ---

// Nova entitat Llista per agrupar categories i productes
data class Llista(
    val id: String = UUID.randomUUID().toString(),
    var nom: String,
    val propietariId: String, // UID de qui la va crear
    val usuarisCompartits: MutableList<String> = mutableListOf() // UIDs/Emails de col·laboradors
)

data class Categoria(
    val id: String = UUID.randomUUID().toString(),
    val llistaId: String, // Vincle amb la Llista pare
    var nom: String
)

data class Producte(
    val id: String = UUID.randomUUID().toString(),
    var idCategoria: String = "",
    var nom: String = "",
    var comprat: Boolean = false,
    var info: String = "" // <--- Nou camp afegit
)


class MainActivity : AppCompatActivity() {

    // --- Variables de la UI ---
    private lateinit var spinnerCategories: Spinner
    private lateinit var recyclerViewProductes: RecyclerView
    private lateinit var producteAdapter: ProducteAdapter
    private lateinit var btnManageCategories: ImageButton

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // --- Estat de l'Aplicació ---
    private var llistaActual: Llista? = null
    private var categoriesDeLaLlista = mutableListOf<Categoria>()
    private var productesDeLaLlista = mutableListOf<Producte>()
    private var categoriaSeleccionadaId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verificació de seguretat: Usuari loguejat?
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Inicialització de Vistes
        spinnerCategories = findViewById(R.id.spinner_categories)
        recyclerViewProductes = findViewById(R.id.recycler_view_productes)
        btnManageCategories = findViewById(R.id.button_manage_categories)

        // Configuració RecyclerView
        producteAdapter = ProducteAdapter(
            mutableListOf(),
            onCheckboxClicked = { producte -> actualitzarEstatProducte(producte) },
            onDeleteClicked = { producte -> eliminarProducte(producte) },
            onEditClicked = { producte -> mostrarDialogEditarProducte(producte) }
        )
        recyclerViewProductes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = producteAdapter
        }

        // Listeners
        findViewById<FloatingActionButton>(R.id.fab_add_producte).setOnClickListener {
            mostrarDialogAfegirProducte()
        }
        btnManageCategories.setOnClickListener {
            mostrarDialogGestioCategories()
        }

        // 🚀 INICI: Carregar Llistes de l'Usuari
        carregarLlistesDeLUsuari()
    }

    // --- MENÚ D'OPCIONS (Tres punts) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "🔄 Canviar Llista")
        menu?.add(0, 2, 0, "➕ Nova Llista")
        menu?.add(0, 3, 0, "📤 Compartir aquesta Llista")
        menu?.add(0, 4, 0, "🚪 Tancar Sessió")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> mostrarDialegSeleccionarLlista()
            2 -> mostrarDialogNovaLlista()
            3 -> mostrarDialogCompartirLlista()
            4 -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // --- GESTIÓ DE LLISTES (Workspaces) ---

    private fun carregarLlistesDeLUsuari() {
        val uid = auth.currentUser!!.uid
        val email = auth.currentUser!!.email ?: ""

        // Busquem llistes on soc propietari OR on estic a la llista de compartits
        // Nota: Firestore té limitacions amb OR queries complexes, així que farem dues consultes o una estructura millor.
        // Per simplicitat, aquí farem una consulta combinada bàsica o ho gestionarem per codi.
        // Una bona pràctica és guardar "listIds" al document de l'usuari, però aquí farem query directa.

        db.collection("llistes")
            .whereEqualTo("propietariId", uid)
            .get()
            .addOnSuccessListener { resultPropies ->
                val llistesTrobades = resultPropies.map {
                    Llista(it.id, it.getString("nom")?:"", it.getString("propietariId")?:"", (it.get("usuarisCompartits") as? List<String>)?.toMutableList() ?: mutableListOf())
                }.toMutableList()

                // També busquem llistes compartides amb el meu EMAIL (més fàcil per compartir)
                db.collection("llistes")
                    .whereArrayContains("usuarisCompartits", email)
                    .get()
                    .addOnSuccessListener { resultCompartides ->
                        val llistesCompartides = resultCompartides.map {
                            Llista(it.id, it.getString("nom")?:"", it.getString("propietariId")?:"", (it.get("usuarisCompartits") as? List<String>)?.toMutableList() ?: mutableListOf())
                        }
                        llistesTrobades.addAll(llistesCompartides)

                        gestionarResultatLlistes(llistesTrobades)
                    }
            }
    }

    private fun gestionarResultatLlistes(llistes: MutableList<Llista>) {
        if (llistes.isEmpty()) {
            // Primera vegada: Creem llista per defecte
            crearLlistaInicial()
        } else {
            // Seleccionem la primera per defecte
            canviarLlistaActual(llistes.first())
        }
    }

    private fun crearLlistaInicial() {
        val uid = auth.currentUser!!.uid
        val novaLlista = Llista(nom = "Llista Personal", propietariId = uid)

        db.collection("llistes").document(novaLlista.id).set(novaLlista)
            .addOnSuccessListener {
                canviarLlistaActual(novaLlista)
                Toast.makeText(this, "Benvingut! S'ha creat la teva llista personal.", Toast.LENGTH_LONG).show()
            }
    }

    private fun mostrarDialogNovaLlista() {
        val input = EditText(this)
        input.hint = "Nom de la nova llista (ex: Supermercat)"

        AlertDialog.Builder(this)
            .setTitle("Crear Nova Llista")
            .setView(wrapInContainer(input))
            .setPositiveButton("Crear") { _, _ ->
                val nom = input.text.toString().trim()
                if (nom.isNotEmpty()) {
                    val novaLlista = Llista(nom = nom, propietariId = auth.currentUser!!.uid)
                    db.collection("llistes").document(novaLlista.id).set(novaLlista)
                        .addOnSuccessListener {
                            canviarLlistaActual(novaLlista)
                            Toast.makeText(this, "Llista '$nom' creada!", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun mostrarDialegSeleccionarLlista() {
        val uid = auth.currentUser!!.uid
        val email = auth.currentUser!!.email ?: ""

        // Recuperem llistes de nou per tenir-les fresques
        // (En una app real utilitzariem un ViewModel o LiveData)
        val llistesTemp = mutableListOf<Llista>()

        // Callback hell simplificat:
        db.collection("llistes").whereEqualTo("propietariId", uid).get().addOnSuccessListener { r1 ->
            llistesTemp.addAll(r1.map { Llista(it.id, it.getString("nom")?:"", it.getString("propietariId")?:"", mutableListOf()) })

            db.collection("llistes").whereArrayContains("usuarisCompartits", email).get().addOnSuccessListener { r2 ->
                llistesTemp.addAll(r2.map { Llista(it.id, it.getString("nom")?:"", it.getString("propietariId")?:"", mutableListOf()) })

                val noms = llistesTemp.map { if(it.propietariId == uid) it.nom else "${it.nom} (Compartida)" }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Els teus espais de compra")
                    .setItems(noms) { _, which ->
                        canviarLlistaActual(llistesTemp[which])
                    }
                    .show()
            }
        }
    }

    private fun mostrarDialogCompartirLlista() {
        val llista = llistaActual ?: return

        val input = EditText(this)
        input.hint = "Email de l'usuari (ex: amic@gmail.com)"

        AlertDialog.Builder(this)
            .setTitle("Compartir '${llista.nom}'")
            .setMessage("Escriu el correu de la persona amb qui vols compartir aquesta llista:")
            .setView(wrapInContainer(input))
            .setPositiveButton("Compartir") { _, _ ->
                val emailDesti = input.text.toString().trim()
                if (emailDesti.isNotEmpty()) {
                    // Afegim l'email a l'array de sharedWith
                    db.collection("llistes").document(llista.id)
                        .update("usuarisCompartits", com.google.firebase.firestore.FieldValue.arrayUnion(emailDesti))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Llista compartida amb $emailDesti", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error en compartir: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun canviarLlistaActual(llista: Llista) {
        llistaActual = llista
        supportActionBar?.title = "🛒 ${llista.nom}" // Canvia el títol de l'app
        carregarDadesDeLaLlista(llista.id)
    }

    // --- CARREGA DE DADES (CATEGORIES I PRODUCTES) ---

    private fun carregarDadesDeLaLlista(llistaId: String) {
        // 1. Carregar Categories d'aquesta llista
        db.collection("categories")
            .whereEqualTo("llistaId", llistaId)
            .get()
            .addOnSuccessListener { resultCat ->
                categoriesDeLaLlista.clear()
                for (doc in resultCat) {
                    categoriesDeLaLlista.add(Categoria(doc.id, doc.getString("llistaId")?:"", doc.getString("nom")?:""))
                }

                // 2. Carregar Productes d'aquesta llista (filtrant per l'ID de categoria o si guardessim llistaId al producte)
                // Com que el producte té idCategoria, primer necessitem les categories, però és més eficient si el producte també tingués llistaId.
                // Per compatibilitat amb el teu codi anterior, agafarem TOTS els productes de les categories trobades.

                // Opció eficient: Guardar llistaId també al Producte.
                // Opció actual (Query manual):
                db.collection("productes")
                    .get() // Això no és òptim en producció (llegir tot), millor afegir whereEqualTo("llistaId", ...)
                    .addOnSuccessListener { resultProd ->
                        productesDeLaLlista.clear()
                        val idsCategoriesDeLaLlista = categoriesDeLaLlista.map { it.id }.toSet()

                        for (doc in resultProd) {
                            val catId = doc.getString("idCategoria") ?: ""
                            // Filtrem en client els productes que pertanyen a les categories de la llista actual
                            if (idsCategoriesDeLaLlista.contains(catId)) {
                                val p = Producte(
                                    doc.id,catId,
                                    doc.getString("nom") ?: "",
                                    doc.getBoolean("comprat") ?: false,
                                    doc.getString("info") ?: "" // <--- Llegim la info de Firestore
                                )
                                productesDeLaLlista.add(p)
                            }
                        }

                        configurarSpinnerCategories()
                    }
            }
    }

    private fun configurarSpinnerCategories() {
        if (categoriesDeLaLlista.isEmpty()) {
            categoriaSeleccionadaId = null
            producteAdapter.updateList(mutableListOf())

            // Adapter buit o missatge
            val llistaBuida = listOf("Crea una categoria...")
            spinnerCategories.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, llistaBuida)
            return
        }

        val noms = categoriesDeLaLlista.map { it.nom }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, noms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategories.adapter = adapter

        // Listener selecció
        spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                categoriaSeleccionadaId = categoriesDeLaLlista[position].id
                filtrarProductesPerCategoria(categoriaSeleccionadaId!!)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Seleccionar el primer per defecte
        if (categoriesDeLaLlista.isNotEmpty()) {
            categoriaSeleccionadaId = categoriesDeLaLlista[0].id
            filtrarProductesPerCategoria(categoriaSeleccionadaId!!)
        }
    }

    private fun filtrarProductesPerCategoria(catId: String) {
        val filtrats = productesDeLaLlista.filter { it.idCategoria == catId }.toMutableList()
        producteAdapter.updateList(filtrats)
    }

    // --- MODIFICACIONS (CRUD) ---

    // Categories
    private fun mostrarDialogGestioCategories() {
        if (llistaActual == null) return

        val accions = categoriesDeLaLlista.map { it.nom } + listOf("➕ Nova Categoria")
        AlertDialog.Builder(this)
            .setTitle("Categories de ${llistaActual?.nom}")
            .setItems(accions.toTypedArray()) { _, which ->
                if (which == accions.size - 1) {
                    mostrarDialogAfegirCategoria()
                } else {
                    mostrarDialogEdicioCategoria(categoriesDeLaLlista[which])
                }
            }
            .setNegativeButton("Tancar", null)
            .show()
    }

    private fun mostrarDialogAfegirCategoria() {
        val input = EditText(this)
        input.hint = "Nom categoria (ex: Carnisseria)"

        AlertDialog.Builder(this)
            .setTitle("Afegir Categoria")
            .setView(wrapInContainer(input))
            .setPositiveButton("Crear") { _, _ ->
                val nom = input.text.toString().trim()
                if (nom.isNotEmpty() && llistaActual != null) {
                    val novaCat = Categoria(llistaId = llistaActual!!.id, nom = nom)
                    db.collection("categories").document(novaCat.id).set(novaCat)
                        .addOnSuccessListener {
                            categoriesDeLaLlista.add(novaCat)
                            configurarSpinnerCategories()
                            // Seleccionar la nova
                            spinnerCategories.setSelection(categoriesDeLaLlista.indexOf(novaCat))
                            Toast.makeText(this, "Categoria creada", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun mostrarDialogEdicioCategoria(cat: Categoria) {
        AlertDialog.Builder(this)
            .setTitle(cat.nom)
            .setItems(arrayOf("Editar nom", "Eliminar")) { _, which ->
                when(which) {
                    0 -> {
                        val input = EditText(this)
                        input.setText(cat.nom)
                        AlertDialog.Builder(this)
                            .setTitle("Editar nom")
                            .setView(wrapInContainer(input))
                            .setPositiveButton("Guardar") { _, _ ->
                                val nouNom = input.text.toString().trim()
                                if (nouNom.isNotEmpty()) {
                                    cat.nom = nouNom
                                    db.collection("categories").document(cat.id).update("nom", nouNom)
                                    configurarSpinnerCategories()
                                }
                            }
                            .show()
                    }
                    1 -> {
                        // Eliminar categoria i productes
                        db.collection("categories").document(cat.id).delete()
                        // Eliminar visualment
                        categoriesDeLaLlista.remove(cat)
                        productesDeLaLlista.removeAll { it.idCategoria == cat.id }
                        configurarSpinnerCategories()

                        // Opcional: Eliminar productes de la DB (Batch write seria millor)
                    }
                }
            }
            .show()
    }

    // Productes
    private fun mostrarDialogAfegirProducte() {
        if (categoriaSeleccionadaId == null) {
            Toast.makeText(this, "Primer crea una categoria!", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = "Nom producte (ex: Llet)"

        AlertDialog.Builder(this)
            .setTitle("Afegir Producte")
            .setView(wrapInContainer(input))
            .setPositiveButton("Afegir") { _, _ ->
                val nom = input.text.toString().trim()
                if (nom.isNotEmpty()) {
                    val nouProd = Producte(idCategoria = categoriaSeleccionadaId!!, nom = nom)
                    // Optimització: Podries guardar llistaId aquí també per facilitar queries

                    db.collection("productes").document(nouProd.id).set(nouProd)
                        .addOnSuccessListener {
                            productesDeLaLlista.add(nouProd)
                            filtrarProductesPerCategoria(categoriaSeleccionadaId!!)
                        }
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun actualitzarEstatProducte(prod: Producte) {
        db.collection("productes").document(prod.id).update("comprat", prod.comprat)
    }

    private fun eliminarProducte(prod: Producte) {
        db.collection("productes").document(prod.id).delete()
        productesDeLaLlista.remove(prod)
        filtrarProductesPerCategoria(prod.idCategoria)
        Toast.makeText(this, "Eliminat", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDialogEditarProducte(prod: Producte) {
        val inputNom = EditText(this)
        inputNom.setText(prod.nom)
        inputNom.hint = "Nom del producte"

        val inputInfo = EditText(this)
        inputInfo.setText(prod.info) // <--- Ara sí mostrem la info actual
        inputInfo.hint = "Informació extra"

        // Layout vertical
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(inputNom)
        layout.addView(inputInfo)

        AlertDialog.Builder(this)
            .setTitle("Editar Producte")
            .setView(wrapInContainer(layout))
            .setPositiveButton("Guardar") { _, _ ->
                val nouNom = inputNom.text.toString().trim()
                val infoExtra = inputInfo.text.toString().trim() // <--- Agafem el text nou

                if (nouNom.isNotEmpty()) {
                    // Actualitzem l'objecte local
                    prod.nom = nouNom
                    prod.info = infoExtra // <--- Actualitzem el model local

                    // Preparem les dades per Firestore
                    val updates = hashMapOf<String, Any>(
                        "nom" to nouNom,
                        "info" to infoExtra // <--- Enviem la info a la base de dades
                    )

                    db.collection("productes").document(prod.id).update(updates)
                    filtrarProductesPerCategoria(prod.idCategoria)
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    // Utilitat UI
    private fun wrapInContainer(view: View): View {
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val margin = (16 * resources.displayMetrics.density).toInt()
        params.setMargins(margin, margin, margin, margin)
        view.layoutParams = params
        container.addView(view)
        return container
    }
}

// --- ADAPTER (Mateix que tenies) ---
class ProducteAdapter(
    private var productes: MutableList<Producte>,
    private val onCheckboxClicked: (Producte) -> Unit,
    private val onDeleteClicked: (Producte) -> Unit,
    private val onEditClicked: (Producte) -> Unit
) : RecyclerView.Adapter<ProducteAdapter.ProducteViewHolder>() {

    inner class ProducteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkboxComprat: CheckBox = itemView.findViewById(R.id.checkbox_comprat)
        val textNomProducte: TextView = itemView.findViewById(R.id.text_nom_producte)
        val buttonDelete: ImageButton = itemView.findViewById(R.id.button_delete)

        fun bind(producte: Producte) {
            textNomProducte.text = producte.nom
            checkboxComprat.setOnCheckedChangeListener(null)
            checkboxComprat.isChecked = producte.comprat
            checkboxComprat.setOnCheckedChangeListener { _, isChecked ->
                producte.comprat = isChecked
                onCheckboxClicked(producte)
            }
            buttonDelete.setOnClickListener { onDeleteClicked(producte) }
            itemView.setOnClickListener { onEditClicked(producte) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProducteViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_producte, parent, false)
        return ProducteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProducteViewHolder, position: Int) {
        holder.bind(productes[position])
    }

    override fun getItemCount(): Int = productes.size

    fun updateList(newList: MutableList<Producte>) {
        productes = newList
        notifyDataSetChanged()
    }
}