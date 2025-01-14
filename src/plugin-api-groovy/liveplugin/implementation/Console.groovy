package liveplugin.implementation

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.InputFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.*
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.ui.components.JBPanel
import liveplugin.implementation.common.IdeUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.awt.*
import java.util.List
import java.util.concurrent.atomic.AtomicReference

import static com.intellij.execution.filters.ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS
import static liveplugin.PluginUtil.invokeOnEDT

class Console {

	static registerConsoleListener(Disposable disposable, Closure callback) {
		registerConsoleFilter(disposable) { consoleText ->
			callback(consoleText)
			null // no filtering of console output
		}
	}

	static registerConsoleFilter(Disposable disposable, Closure callback) {
		registerConsoleFilter(disposable, new InputFilter() {
			@Override List<Pair<String, ConsoleViewContentType>> applyFilter(String consoleText, ConsoleViewContentType contentType) {
				def newConsoleText = callback(consoleText)
				if (newConsoleText == null) null
				else [new Pair(newConsoleText, contentType)]
			}
		})
	}

	static registerConsoleFilter(Disposable disposable, InputFilter inputFilter) {
		def extensionPoint = ApplicationManager.application.extensionArea.getExtensionPoint(INPUT_FILTER_PROVIDERS)
		extensionPoint.registerExtension(consoleFilterProviderFor(inputFilter), LoadingOrder.FIRST, disposable)
	}

	private static ConsoleInputFilterProvider consoleFilterProviderFor(InputFilter inputFilter) {
		new ConsoleInputFilterProvider() {
			@Override InputFilter[] getDefaultFilters(@NotNull Project project) {
				[inputFilter]
			}
		}
	}

	static ConsoleView showInConsole(
        @Nullable message,
		String consoleTitle = "",
		@NotNull Project project,
		ConsoleViewContentType contentType = guessContentTypeOf(message),
        int scrollTo = -1
	) {
		AtomicReference<ConsoleView> result = new AtomicReference(null)
		// Use reference for consoleTitle because get groovy Reference class like in this bug http://jira.codehaus.org/browse/GROOVY-5101
		AtomicReference<String> titleRef = new AtomicReference(consoleTitle)

		invokeOnEDT {
			def consoleView = TextConsoleBuilderFactory.instance.createBuilder(project).console
			consoleView.print(Misc.asString(message), contentType)

			DefaultActionGroup toolbarActions = new DefaultActionGroup()
			//noinspection GroovyUnusedAssignment (IntelliJ is wrong and has been wrong for years now :()
			def consoleComponent = new MyConsolePanel(consoleView, toolbarActions)
			RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, null, consoleComponent, titleRef.get()) {
				@Override boolean isContentReuseProhibited() { true }
				@Override Icon getIcon() { AllIcons.Nodes.Plugin }
			}
			Executor executor = DefaultRunExecutor.runExecutorInstance

			toolbarActions.add(new CloseAction(executor, descriptor, project))
			consoleView.createConsoleActions().each { toolbarActions.add(it) }

			RunContentManager.getInstance(project).showRunContent(executor, descriptor)
			result.set(consoleView)
            if (scrollTo >= 0) console.scrollTo(scrollTo)
        }
		result.get()
	}

	static ConsoleViewContentType guessContentTypeOf(text) {
		text instanceof Throwable ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT
	}

	private static class MyConsolePanel extends JBPanel<MyConsolePanel> {
		MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
			super(new BorderLayout())
			def toolbarPanel = new JBPanel(new BorderLayout())
			toolbarPanel.add(ActionManager.instance.createActionToolbar(IdeUtil.livePluginActionPlace, toolbarActions, false).component)
			add(toolbarPanel, BorderLayout.WEST)
			add(consoleView.component, BorderLayout.CENTER)
		}
	}
}
